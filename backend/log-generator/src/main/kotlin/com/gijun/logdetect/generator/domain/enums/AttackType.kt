package com.gijun.logdetect.generator.domain.enums

/**
 * log-detective 탐지 엔진 6개 규칙(R001~R006)과 1:1 매칭되는 공격 시나리오.
 * LogEventFactory 가 각 타입에 맞는 로그 이벤트를 합성한다.
 */
enum class AttackType {
    /** R001 BruteForceLoginRule — 동일 IP 로그인 실패 반복 */
    BRUTE_FORCE,

    /** R002 SqlInjectionPatternRule — message 에 SQLi 시그니처 주입 */
    SQL_INJECTION,

    /** R003 ErrorRateSpikeRule — 특정 source 의 ERROR 급증 */
    ERROR_SPIKE,

    /** R004 OffHourAccessRule — 관리계정의 00~05시(KST) 로그인 성공 */
    OFF_HOUR_ACCESS,

    /** R005 GeoAnomalyRule — 동일 userId 가 물리적으로 불가능한 거리에서 접근 */
    GEO_ANOMALY,

    /** R006 RareEventRule — 드문 source + level + pattern 조합 */
    RARE_EVENT,
}
