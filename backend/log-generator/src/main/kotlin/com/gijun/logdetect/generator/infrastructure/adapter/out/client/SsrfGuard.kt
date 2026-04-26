package com.gijun.logdetect.generator.infrastructure.adapter.out.client

import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

/**
 * SSRF (Server-Side Request Forgery) 가드 헬퍼.
 *
 * `targetUrl` 이 외부 입력으로 변경 가능한 환경에서 (또는 yml 이 외부 주입 환경변수로 채워지는 경우)
 * 사설망 / 메타데이터 엔드포인트로의 의도치 않은 요청을 사전에 차단한다.
 *
 * - 운영 환경에 따라 사설망(internal cluster) 도 정상 타겟이 될 수 있으므로
 *   `allowPrivateNetwork` 플래그로 토글한다.
 * - 호스트명만으로는 우회 가능하므로 (DNS rebinding) `InetAddress.getAllByName` 결과 전체를 검사한다.
 * - 클라우드 메타데이터 엔드포인트 (169.254.169.254) 는 `allowPrivateNetwork` 와 무관하게 항상 차단.
 */
object SsrfGuard {

    private const val AWS_METADATA_IP = "169.254.169.254"

    /**
     * 클라우드 메타데이터 엔드포인트 — `allowPrivateNetwork` 와 무관하게 차단한다.
     * AWS / GCP / Azure 모두 link-local (169.254.0.0/16) 의 특정 주소를 사용.
     */
    private val alwaysBlockedHosts = setOf(
        AWS_METADATA_IP, // AWS / GCP / Azure 메타데이터 공통
        "fd00:ec2::254", // AWS IMDS IPv6
    )

    fun validateUrl(
        url: String,
        allowedHosts: List<String> = emptyList(),
        allowPrivateNetwork: Boolean = false,
    ) {
        val uri = runCatching { URI.create(url) }.getOrElse {
            throw SsrfViolationException("URL 형식이 올바르지 않습니다: $url")
        }

        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            throw SsrfViolationException("허용되지 않는 스키마: $scheme (http/https 만 허용)")
        }

        val host = uri.host
            ?: throw SsrfViolationException("호스트가 없는 URL: $url")

        // 화이트리스트가 있고 일치하면 즉시 통과 (내부 LB 도메인 등)
        if (allowedHosts.isNotEmpty() && allowedHosts.any { it.equals(host, ignoreCase = true) }) {
            return
        }

        val resolved = try {
            InetAddress.getAllByName(host)
        } catch (e: UnknownHostException) {
            throw SsrfViolationException("호스트 해석 실패: $host", e)
        }

        for (addr in resolved) {
            val ip = addr.hostAddress
            if (ip in alwaysBlockedHosts || isMetadataAddress(addr)) {
                throw SsrfViolationException("메타데이터 엔드포인트 차단: $host → $ip")
            }
            if (!allowPrivateNetwork && isBlockedAddress(addr)) {
                throw SsrfViolationException(
                    "사설/루프백/링크로컬 주소 차단: $host → $ip " +
                        "(generator.ssrf.allow-private-network=true 로 허용 가능)",
                )
            }
        }
    }

    private fun isMetadataAddress(addr: InetAddress): Boolean {
        // 169.254.169.254 등 link-local 메타데이터
        return addr.hostAddress == AWS_METADATA_IP
    }

    private fun isBlockedAddress(addr: InetAddress): Boolean {
        return addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress || // 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
            addr.isMulticastAddress
    }
}

class SsrfViolationException(message: String, cause: Throwable? = null) : SecurityException(message, cause)
