package com.gijun.logdetect.ingest.application.util

import com.gijun.logdetect.common.domain.model.LogEvent
import java.net.InetAddress
import java.net.UnknownHostException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Outbox payload 적재 전 LogEvent 의 PII 필드를 마스킹/익명화하기 위한 유틸.
 *
 * WHY — `outbox_messages.payload (jsonb)` 에 LogEvent 전체 (ip, userId, message,
 * attributes) 가 평문으로 저장되면, retention 잡 누락 시 GDPR / 개인정보보호법
 * 보관 기한을 초과한다. 1차 방어선으로 정규식/규칙 기반 마스킹과 HMAC pseudonymization
 * 을 함께 제공한다.
 *
 * ### 정책 강화 (이슈 #88)
 * - IPv4: WP29/EDPB 권고에 따라 `/16` 잔존 (`A.B.*.*`) — 기존 `/24` 는 ISP 단위
 *   재식별 위험이 있어 폐기. HASH 모드는 HMAC-SHA256(salt) 로 완전 비가역 치환.
 * - IPv6: 앞 64bit (network prefix) 만 보존하고 뒤 64bit (interface id) 마스킹.
 *   IPv4-mapped IPv6 는 IPv4 규칙으로 위임.
 * - userId: prefix 보존을 제거 — 길이 보존 `*` 또는 HMAC 만 노출. 4글자 prefix 는
 *   사전 공격 / 역추적이 가능하므로 GDPR 익명화 기준에 미치지 못한다.
 *
 * ### 모드 (운영 결정)
 * - [PiiMode.MASK] — 사람이 읽을 수 있는 형태 유지. 다운스트림이 IP/userId 로
 *   집계는 못 하지만 디버깅 시 대략적 위치/소속 확인 가능.
 * - [PiiMode.HASH] — HMAC-SHA256(salt) 로 deterministic pseudonym 생성.
 *   동일 salt 하에서 동일 입력 → 동일 출력이므로 컨슈머가 그룹화 가능.
 *   salt 누설 시 rainbow table 공격으로 IP/userId 복원될 수 있어 salt 회전 필요.
 *
 * 위치 — 도메인 모델 (LogEvent) 변환 정책이며 프레임워크 의존이 없으므로 application/util
 * 에 둔다. handler / serializer 어댑터에서 직접 호출한다.
 */
object PiiMasker {

    private const val IP_OCTET_PLACEHOLDER = "*"
    private const val IPV6_GROUPS = 8
    private const val IPV6_PREFIX_GROUPS = 4
    private const val IPV6_GROUP_SHIFT = 8
    private const val BYTE_MASK = 0xff
    private const val HMAC_ALGO = "HmacSHA256"
    private const val HMAC_PREFIX = "h$"
    private const val HMAC_HEX_LEN = 32
    private val IPV4_OCTETS = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /**
     * IP 주소를 정책에 따라 마스킹/해시한다.
     *
     * - IPv4 + [PiiMode.MASK] → `A.B.*.*` (`/16` 잔존)
     * - IPv6 + [PiiMode.MASK] → `g1:g2:g3:g4:*:*:*:*` (앞 64bit 잔존)
     * - [PiiMode.HASH] → `h$<hex32>` HMAC-SHA256(salt) prefix 32자
     *
     * 인식 불가능한 포맷은 [PiiMode.MASK] 시 원본 유지, [PiiMode.HASH] 시 원문 해시.
     * null/blank 는 그대로 반환한다.
     */
    fun maskIp(ip: String?, mode: PiiMode = PiiMode.MASK, salt: String = ""): String? {
        if (ip.isNullOrBlank()) return ip
        if (mode == PiiMode.HASH) return hmac(ip, salt)

        val v4 = IPV4_OCTETS.matchEntire(ip)
        if (v4 != null) {
            val (a, b, _, _) = v4.destructured
            return "$a.$b.$IP_OCTET_PLACEHOLDER.$IP_OCTET_PLACEHOLDER"
        }
        return maskIpv6(ip) ?: ip
    }

    /**
     * userId 를 정책에 따라 마스킹/해시한다.
     *
     * - [PiiMode.MASK] → 길이 보존 `*` (prefix 미노출 — 이슈 #88 강화)
     * - [PiiMode.HASH] → `h$<hex32>` HMAC-SHA256(salt)
     *
     * 기존 4자 prefix 보존은 brute-force 사전 공격 / 가입 패턴 추정 위험이 있어 제거.
     */
    fun maskUserId(userId: String?, mode: PiiMode = PiiMode.MASK, salt: String = ""): String? {
        if (userId.isNullOrBlank()) return userId
        if (mode == PiiMode.HASH) return hmac(userId, salt)
        return IP_OCTET_PLACEHOLDER.repeat(userId.length)
    }

    /**
     * LogEvent 의 PII 필드 (ip, userId) 를 정책에 따라 변환한 사본을 반환한다.
     * message / attributes 는 정규식 검출이 어려워 1차 방어선에서 제외 — retention
     * 잡과 access control 로 보완한다.
     */
    fun mask(event: LogEvent, mode: PiiMode = PiiMode.MASK, salt: String = ""): LogEvent =
        event.copy(
            ip = maskIp(event.ip, mode, salt),
            userId = maskUserId(event.userId, mode, salt),
        )

    /**
     * IPv6 앞 64bit 보존, 뒤 64bit `*` 처리. `::` 압축, IPv4-mapped 모두 정규화 후 처리.
     * 파싱 실패 시 null — 호출 측에서 원본 유지 정책으로 fallback.
     */
    private fun maskIpv6(ip: String): String? {
        val addr = try {
            InetAddress.getByName(ip)
        } catch (_: UnknownHostException) {
            return null
        }
        val bytes = addr.address
        // IPv4-mapped 는 4바이트로 반환되므로 IPv6 처리 대상이 아니다.
        if (bytes.size != IPV6_GROUPS * 2) return null

        val groups = (0 until IPV6_GROUPS).map { idx ->
            val hi = bytes[idx * 2].toInt() and BYTE_MASK
            val lo = bytes[idx * 2 + 1].toInt() and BYTE_MASK
            ((hi shl IPV6_GROUP_SHIFT) or lo)
        }
        val head = groups.take(IPV6_PREFIX_GROUPS)
            .joinToString(":") { it.toString(16) }
        val tail = List(IPV6_GROUPS - IPV6_PREFIX_GROUPS) { IP_OCTET_PLACEHOLDER }
            .joinToString(":")
        return "$head:$tail"
    }

    /**
     * HMAC-SHA256(salt) hex prefix [HMAC_HEX_LEN] 자를 `h$` 접두로 감싸 반환.
     *
     * salt 가 비어 있으면 [SecretKeySpec] 이 [IllegalArgumentException] 을 던지므로
     * 부팅 검증 누락 대비로 단일 placeholder byte 를 사용한다 (보안 강도는 떨어지나
     * 런타임 NPE 보다 안전). HASH 모드 운영 시 호출 측에서 salt 비어있음을 검증한다.
     */
    private fun hmac(value: String, salt: String): String {
        val keyBytes = salt.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            ?: byteArrayOf(0)
        val key = SecretKeySpec(keyBytes, HMAC_ALGO)
        val mac = Mac.getInstance(HMAC_ALGO).apply { init(key) }
        val digest = mac.doFinal(value.toByteArray(Charsets.UTF_8))
        val hex = buildString(digest.size * 2) {
            digest.forEach { b -> append("%02x".format(b.toInt() and BYTE_MASK)) }
        }
        return HMAC_PREFIX + hex.take(HMAC_HEX_LEN)
    }
}

/**
 * PII 처리 모드.
 *
 * - [MASK] — 가독성 유지 마스킹 (`A.B.*.*`, `****`).
 * - [HASH] — HMAC-SHA256(salt) deterministic pseudonymization.
 */
enum class PiiMode {
    MASK,
    HASH,
}
