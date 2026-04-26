-- outbox_aggregate_idx 는 V2 에서 운영 디버깅용으로 추가됐으나
-- INSERT 핫패스마다 인덱스 유지 비용을 발생시킨다.
-- 폴러는 partial index(outbox_pending_idx) 만 사용하고 aggregate_id 조회는 거의 일어나지 않으므로 제거한다.
--
-- 운영 디버깅이 필요할 때만 ad-hoc 으로 생성한다 (배타 락 회피를 위해 CONCURRENTLY 권장):
--   CREATE INDEX CONCURRENTLY outbox_aggregate_idx ON outbox_messages (aggregate_id);
--   -- 디버깅 종료 후
--   DROP INDEX CONCURRENTLY IF EXISTS outbox_aggregate_idx;
drop index if exists outbox_aggregate_idx;
