package com.gijun.logdetect.common.util

import java.security.MessageDigest

object Fingerprint {
    fun sha256(vararg parts: String?): String {
        val raw = parts.joinToString("|") { it ?: "" }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
