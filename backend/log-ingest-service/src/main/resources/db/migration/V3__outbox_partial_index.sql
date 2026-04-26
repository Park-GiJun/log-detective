-- 기존 일반 인덱스는 PUBLISHED/DEAD 행까지 포함되어 시간이 지날수록 비대해진다.
-- 폴러는 PENDING / FAILED + next_attempt_at <= now() 만 조회하므로 partial index 로 교체하여
-- 인덱스 크기와 스캔 비용을 일정하게 유지한다.
drop index if exists outbox_pending_idx;

create index outbox_pending_idx on outbox_messages (next_attempt_at)
    where status in ('PENDING', 'FAILED');
