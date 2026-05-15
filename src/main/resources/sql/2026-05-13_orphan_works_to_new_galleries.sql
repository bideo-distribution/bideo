-- ============================================================
-- 2026-05-13 — 고아 작품(예술관 없는 작품) 처리 (C 방식)
-- ============================================================
-- 도메인 룰: BIDEO 에서 작품은 반드시 어떤 예술관에 속해야 한다.
--           매핑이 없는 작품은 잘못된 상태이므로, 작가별로 새 갤러리를
--           생성하고 그 갤러리에 작가의 모든 고아 작품을 그룹 매핑한다.
--
-- 동작:
--   1) 고아 작품 임시 테이블 생성
--   2) 고아 작품이 있는 작가별로 갤러리 1개 신규 생성 ("{닉네임}의 작품 모음")
--   3) 고아 작품을 신규 갤러리에 매핑 (sort_order = 작품 id 오름차순)
--   4) tbl_gallery.cover_image, work_count + tbl_member.gallery_count 재동기화
-- 트랜잭션으로 묶여 있어 실패 시 전체 롤백.
-- ============================================================

begin;

\echo '=> 정리 전 통계'
select 'works_total'    as t, count(*) from tbl_work
 where status='ACTIVE' and deleted_datetime is null
union all
select 'works_orphan',  count(*) from tbl_work w
 where w.status='ACTIVE' and w.deleted_datetime is null
   and not exists (
       select 1 from tbl_gallery_work gw
         join tbl_gallery g on g.id=gw.gallery_id
                            and g.deleted_datetime is null
                            and g.status='EXHIBITING'
        where gw.work_id = w.id
   );

-- ============================================================
-- 1) 고아 작품 목록
-- ============================================================
drop table if exists _orphan_works;
create temp table _orphan_works as
select w.id, w.member_id
  from tbl_work w
 where w.status = 'ACTIVE'
   and w.deleted_datetime is null
   and not exists (
       select 1 from tbl_gallery_work gw
         join tbl_gallery g on g.id = gw.gallery_id
                            and g.deleted_datetime is null
                            and g.status = 'EXHIBITING'
        where gw.work_id = w.id
   );

-- ============================================================
-- 2) 고아 작품 보유 작가별 신규 갤러리 1개 생성
-- ============================================================
drop table if exists _new_galleries;
create temp table _new_galleries as
with inserted as (
    insert into tbl_gallery (
        member_id, title, description,
        allow_comment, show_similar,
        work_count, like_count, comment_count, view_count,
        status, created_datetime, updated_datetime
    )
    select
        m.id,
        m.nickname || '의 작품 모음',
        '작가가 직접 큐레이션한 작품 모음',
        true, true,
        0, 0, 0, 0,
        'EXHIBITING', now(), now()
    from (select distinct member_id from _orphan_works) o
    join tbl_member m on m.id = o.member_id
    returning id, member_id
)
select id, member_id from inserted;

-- ============================================================
-- 3) cover_image 채우기 (기존 시드 패턴과 동일)
-- ============================================================
update tbl_gallery
   set cover_image = 'https://picsum.photos/seed/g' || id::text || '/640/400'
 where (cover_image is null or cover_image = '')
   and id in (select id from _new_galleries);

-- ============================================================
-- 4) 고아 작품을 신규 갤러리에 매핑
-- ============================================================
insert into tbl_gallery_work (gallery_id, work_id, sort_order, added_at)
select ng.id,
       ow.id,
       row_number() over (partition by ng.id order by ow.id) - 1 as sort_order,
       now()
  from _orphan_works ow
  join _new_galleries ng on ng.member_id = ow.member_id
on conflict (gallery_id, work_id) do nothing;

-- ============================================================
-- 5) 비정규화 카운트 재동기화
-- ============================================================
update tbl_gallery g set work_count = 0;
update tbl_gallery g
   set work_count = coalesce(c.cnt, 0)
  from (select gallery_id, count(*) cnt
          from tbl_gallery_work group by gallery_id) c
 where g.id = c.gallery_id;

update tbl_member m set gallery_count = 0;
update tbl_member m
   set gallery_count = coalesce(c.cnt, 0)
  from (select member_id, count(*) cnt
          from tbl_gallery
         where deleted_datetime is null
         group by member_id) c
 where m.id = c.member_id;

\echo '=> 정리 후 통계'
select 'works_total'    as t, count(*) from tbl_work
 where status='ACTIVE' and deleted_datetime is null
union all
select 'works_in_gallery',
       count(distinct w.id) from tbl_work w
       join tbl_gallery_work gw on gw.work_id = w.id
       join tbl_gallery g on g.id = gw.gallery_id
                          and g.deleted_datetime is null
                          and g.status = 'EXHIBITING'
 where w.status='ACTIVE' and w.deleted_datetime is null
union all
select 'works_orphan_remaining',
       count(*) from tbl_work w
 where w.status='ACTIVE' and w.deleted_datetime is null
   and not exists (
       select 1 from tbl_gallery_work gw
         join tbl_gallery g on g.id=gw.gallery_id
                            and g.deleted_datetime is null
                            and g.status='EXHIBITING'
        where gw.work_id = w.id
   )
union all
select 'new_galleries_created', count(*) from _new_galleries
union all
select 'gallery_total',
       count(*) from tbl_gallery where deleted_datetime is null;

commit;
