-- ============================================================
-- 2026-05-13 — 예술관에 잘못 매핑된 '다른 작가의 작품' 정리
-- ============================================================
-- 배경: seed_presentation_v3 가 lateral UNION 으로 본인 작품 풀과 전체 작품 풀을
--       합친 뒤 random LIMIT 했기 때문에, 본인 작품이 충분해도 다른 작가 작품이
--       섞여 들어갔다. seed 파일은 별도 커밋에서 수정 — 이 파일은 기존 DB 정리용.
--
-- 동작: tbl_gallery_work 에서 gallery.member_id 와 work.member_id 가 일치하지
--       않는 row 를 모두 삭제 → tbl_gallery.work_count 를 실제 매핑 수로 보정.
-- ============================================================

begin;

-- 0. 정리 전 통계 (실행 로그 확인용)
select
    count(*) filter (where w.member_id = g.member_id) as ok_rows,
    count(*) filter (where w.member_id <> g.member_id) as foreign_rows
from tbl_gallery_work gw
join tbl_gallery g on g.id = gw.gallery_id
join tbl_work    w on w.id = gw.work_id;

-- 1. 다른 작가 작품 매핑 삭제
delete from tbl_gallery_work gw
 using tbl_gallery g, tbl_work w
 where g.id = gw.gallery_id
   and w.id = gw.work_id
   and w.member_id <> g.member_id;

-- 2. tbl_gallery.work_count 재동기화 (매핑이 줄어든 만큼 work_count 도 줄어듦)
update tbl_gallery g
   set work_count = coalesce(c.cnt, 0)
  from (
    select gw.gallery_id, count(*) as cnt
      from tbl_gallery_work gw
      join tbl_work w on w.id = gw.work_id
     where w.deleted_datetime is null
       and w.status <> 'DELETED'
     group by gw.gallery_id
  ) c
 where g.id = c.gallery_id;

-- 매핑이 0건이 된 갤러리는 위 update 의 from 절에 포함되지 않으므로 별도로 0 처리
update tbl_gallery g
   set work_count = 0
 where g.deleted_datetime is null
   and not exists (
     select 1 from tbl_gallery_work gw where gw.gallery_id = g.id
   );

-- 3. 정리 후 통계
select
    count(*) filter (where w.member_id = g.member_id) as ok_rows,
    count(*) filter (where w.member_id <> g.member_id) as foreign_rows
from tbl_gallery_work gw
join tbl_gallery g on g.id = gw.gallery_id
join tbl_work    w on w.id = gw.work_id;

commit;
