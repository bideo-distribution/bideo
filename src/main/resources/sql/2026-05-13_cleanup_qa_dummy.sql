-- ============================================================
-- 2026-05-13 — QA 더미 데이터 광범위 정리 (B 방식, 발표 전 청소)
-- ============================================================
-- 출처:
--   - seed_admin_dummy.sql       — 회원 100 + 작품/경매/주문/결제/출금 등
--   - seed_admin_photo_works.sql — QA-사진 작품 15건
--   - seed_presentation_v3       — QA 회원 id(2~101)에 정상 작품도 시드됨
--
-- 동작 (의존성 역순):
--   1) QA 회원이 buyer/seller/winner/holder 인 결제·주문·출금·정산
--   2) QA 회원의 경매(+bid, wishlist) — 작품 소유 무관
--   3) QA 회원이 소유한 모든 작품 + 그 자식 (file/tag/view/like/bookmark/comment/hide/gallery_work)
--   4) QA 회원이 소유한 갤러리 + 그 자식
--   5) QA 회원의 공모전 / 출품
--   6) QA 회원의 신고/문의/제재/카드/oauth/알림/메시지/메타/follow/block 등
--   7) QA 회원 본인
--   8) 비정규화 카운트 재동기화
--
-- 트랜잭션으로 묶임 — 실패 시 전체 롤백.
-- ============================================================

begin;

\echo '=> 정리 전 통계'
select 'qa_members' as t, count(*) from tbl_member where email LIKE 'qa%@bideo.com'
union all select 'qa_works(prefix)',
    count(*) from tbl_work where title like 'QA-%'
union all select 'qa_works(owned)',
    count(*) from tbl_work where member_id in (select id from tbl_member where email LIKE 'qa%@bideo.com')
union all select 'qa_auctions(by_member)',
    count(*) from tbl_auction where seller_id in (select id from tbl_member where email LIKE 'qa%@bideo.com')
                                  or winner_id in (select id from tbl_member where email LIKE 'qa%@bideo.com');

-- ============================================================
-- 0) 임시 테이블 — 식별자 모음
-- ============================================================
drop table if exists _qa_members;
drop table if exists _qa_works;
drop table if exists _qa_galleries;
drop table if exists _qa_auctions;
drop table if exists _qa_orders;
drop table if exists _qa_payments;
drop table if exists _qa_contests;

create temp table _qa_members as
select id from tbl_member where email LIKE 'qa%@bideo.com';

create temp table _qa_works as
select id from tbl_work
 where title like 'QA-%'
    or member_id in (select id from _qa_members);

create temp table _qa_galleries as
select id from tbl_gallery where member_id in (select id from _qa_members);

create temp table _qa_auctions as
select id from tbl_auction
 where work_id   in (select id from _qa_works)
    or seller_id in (select id from _qa_members)
    or winner_id in (select id from _qa_members);

create temp table _qa_orders as
select id, order_code from tbl_order
 where order_code like 'ORD-QA-%'
    or buyer_id   in (select id from _qa_members)
    or seller_id  in (select id from _qa_members);

create temp table _qa_payments as
select id from tbl_payment
 where payment_code like 'PAY-QA-%'
    or buyer_id   in (select id from _qa_members)
    or seller_id  in (select id from _qa_members);

create temp table _qa_contests as
select id from tbl_contest where member_id in (select id from _qa_members);

-- ============================================================
-- 1) 정산 (settlement → deduction)
-- ============================================================
delete from tbl_settlement_deduction where settlement_id in (
  select id from tbl_settlement
   where payment_id in (select id from _qa_payments)
      or member_id  in (select id from _qa_members)
);
delete from tbl_settlement
 where payment_id in (select id from _qa_payments)
    or member_id  in (select id from _qa_members);

-- ============================================================
-- 2) 결제 / 주문 / 출금
-- ============================================================
delete from tbl_payment where id in (select id from _qa_payments);
delete from tbl_order   where id in (select id from _qa_orders);
delete from tbl_withdrawal_request
 where withdrawal_code like 'WDL-QA-%'
    or member_id in (select id from _qa_members);

-- ============================================================
-- 3) 경매 (bid → wishlist → auction)
-- ============================================================
delete from tbl_bid
 where auction_id in (select id from _qa_auctions)
    or member_id  in (select id from _qa_members);
delete from tbl_auction_wishlist
 where auction_id in (select id from _qa_auctions)
    or member_id  in (select id from _qa_members);
delete from tbl_auction where id in (select id from _qa_auctions);

-- ============================================================
-- 4) 작품 자식
-- ============================================================
delete from tbl_work_file where work_id in (select id from _qa_works);
delete from tbl_work_tag  where work_id in (select id from _qa_works);
delete from tbl_work_view where work_id in (select id from _qa_works);
delete from tbl_work_like where work_id in (select id from _qa_works);

delete from tbl_bookmark
 where target_type = 'WORK' and target_id in (select id from _qa_works);

-- 작품 댓글 좋아요 → 댓글 → hide
delete from tbl_comment_like where comment_id in (
  select id from tbl_comment
   where target_type = 'WORK' and target_id in (select id from _qa_works)
);
delete from tbl_comment
 where target_type = 'WORK' and target_id in (select id from _qa_works);

delete from tbl_hide
 where target_type = 'WORK' and target_id in (select id from _qa_works);

delete from tbl_gallery_work where work_id in (select id from _qa_works);

delete from tbl_work where id in (select id from _qa_works);

-- ============================================================
-- 5) 갤러리 자식 → 갤러리
-- ============================================================
delete from tbl_gallery_work where gallery_id in (select id from _qa_galleries);
delete from tbl_gallery_like where gallery_id in (select id from _qa_galleries);
delete from tbl_gallery_tag  where gallery_id in (select id from _qa_galleries);
delete from tbl_gallery_view where gallery_id in (select id from _qa_galleries);

delete from tbl_comment_like where comment_id in (
  select id from tbl_comment
   where target_type = 'GALLERY' and target_id in (select id from _qa_galleries)
);
delete from tbl_comment
 where target_type = 'GALLERY' and target_id in (select id from _qa_galleries);

delete from tbl_gallery where id in (select id from _qa_galleries);

-- ============================================================
-- 6) 공모전 (자식 → 본체)
-- ============================================================
delete from tbl_contest_entry where contest_id in (select id from _qa_contests)
                                  or member_id in (select id from _qa_members);
delete from tbl_contest_tag   where contest_id in (select id from _qa_contests);
delete from tbl_contest       where id         in (select id from _qa_contests);

-- ============================================================
-- 7) QA 회원의 잔여 자식 (모든 곳)
-- ============================================================
-- 신고
delete from tbl_report
 where reporter_id in (select id from _qa_members)
    or (target_type = 'MEMBER' and target_id in (select id from _qa_members))
    or detail like 'QA 더미 신고%';

-- 문의 / 제재 / 카드 / oauth
delete from tbl_inquiry
 where member_id in (select id from _qa_members) or content like 'QA 문의 본문%';
delete from tbl_member_restriction where member_id in (select id from _qa_members);
delete from tbl_card  where member_id in (select id from _qa_members);
delete from tbl_oauth where member_id in (select id from _qa_members);

-- 알림
delete from tbl_notification
 where member_id in (select id from _qa_members)
    or sender_id in (select id from _qa_members);
delete from tbl_notification_setting where member_id in (select id from _qa_members);

-- 메시지
delete from tbl_message_like where message_id in (
  select id from tbl_message where sender_id in (select id from _qa_members)
);
delete from tbl_message where sender_id in (select id from _qa_members);
delete from tbl_message_room_member where member_id in (select id from _qa_members);

-- 메타
delete from tbl_member_tag     where member_id in (select id from _qa_members);
delete from tbl_member_badge   where member_id in (select id from _qa_members);
delete from tbl_search_history where member_id in (select id from _qa_members);

-- QA 회원이 남긴 다른 객체 대상의 행위 (work/gallery 가 본인 것이 아니어도)
delete from tbl_follow
 where follower_id  in (select id from _qa_members)
    or following_id in (select id from _qa_members);
delete from tbl_block
 where blocker_id in (select id from _qa_members)
    or blocked_id in (select id from _qa_members);
delete from tbl_work_like    where member_id in (select id from _qa_members);
delete from tbl_work_view    where member_id in (select id from _qa_members);
delete from tbl_gallery_like where member_id in (select id from _qa_members);
delete from tbl_gallery_view where member_id in (select id from _qa_members);
delete from tbl_bookmark     where member_id in (select id from _qa_members);
delete from tbl_comment_like where member_id in (select id from _qa_members);
delete from tbl_comment      where member_id in (select id from _qa_members);
delete from tbl_hide         where member_id in (select id from _qa_members);

-- ============================================================
-- 8) 회원 본체
-- ============================================================
delete from tbl_member where id in (select id from _qa_members);

-- ============================================================
-- 9) 비정규화 재동기화 — 카운트가 음수/잔여 없게
-- ============================================================
update tbl_member m set follower_count = 0;
update tbl_member m
   set follower_count = coalesce(c.cnt, 0)
  from (select following_id, count(*) cnt from tbl_follow group by following_id) c
 where m.id = c.following_id;

update tbl_member m set following_count = 0;
update tbl_member m
   set following_count = coalesce(c.cnt, 0)
  from (select follower_id, count(*) cnt from tbl_follow group by follower_id) c
 where m.id = c.follower_id;

update tbl_member m set gallery_count = 0;
update tbl_member m
   set gallery_count = coalesce(c.cnt, 0)
  from (select member_id, count(*) cnt from tbl_gallery
         where deleted_datetime is null group by member_id) c
 where m.id = c.member_id;

update tbl_gallery g set work_count = 0;
update tbl_gallery g
   set work_count = coalesce(c.cnt, 0)
  from (select gallery_id, count(*) cnt from tbl_gallery_work group by gallery_id) c
 where g.id = c.gallery_id;

\echo '=> 정리 후 통계'
select 'qa_members' as t, count(*) from tbl_member where email LIKE 'qa%@bideo.com'
union all select 'qa_works(prefix)',
    count(*) from tbl_work where title like 'QA-%'
union all select 'qa_works(owned)',
    count(*) from tbl_work where member_id in (
      select id from tbl_member where email LIKE 'qa%@bideo.com'
    )
union all select 'members_total',  count(*) from tbl_member
union all select 'works_total',    count(*) from tbl_work
union all select 'auctions_total', count(*) from tbl_auction
union all select 'orders_total',   count(*) from tbl_order
union all select 'payments_total', count(*) from tbl_payment;

commit;
