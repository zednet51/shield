package com.aggregatorx.shielded.engine.token

import android.util.Base64
import com.aggregatorx.shielded.data.db.TokenDao
import com.aggregatorx.shielded.data.model.AuthTokenEntity
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automated token discovery, decoding (Base64/Base44), and session replay.
 *
 * Scans raw HTML/JS blobs for:
 *  - JWT patterns (eyJ…)
 *  - Authorization header values
 *  - CSRF tokens in meta/input tags
 *  - Base64-encoded credential blobs
 *  - Base44-encoded tokens (custom alphabet used by some CDNs)
 */
@Singleton
class TokenManager @Inject constructor(
    private val tokenDao: TokenDao
) {
    companion object {
        private val JWT_REGEX = Regex("""eyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+""")
        private val BEARER_REGEX = Regex("""[Bb]earer\s+([A-Za-z0-9\-._~+/]+=*)""")
        private val BASE64_BLOB = Regex("""[A-Za-z0-9+/]{32,}={0,2}""")
        // Base44 alphabet used by some streaming CDNs
        private val BASE44_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGH"
        private val BASE44_REGEX = Regex("""[0-9a-zA-BCDEFGH]{24,}""")
        private val AUTH_HEADER_REGEX = Regex(""""[Aa]uthorization"\s*:\s*"([^"]+)"""")
        private val CSRF_REGEX = Regex("""(?:csrf[_\-]?token|_token|csrfmiddlewaretoken)['":\s=]+([A-Za-z0-9\-_]{16,})""", RegexOption.IGNORE_CASE)
    }

    /** Scan a raw HTML/JS string and persist any discovered tokens. */
    suspend fun scanAndPersist(sourceUrl: String, rawContent: String) {
        val host = try { URL(sourceUrl).host } catch (_: Exception) { sourceUrl }
        val found = mutableListOf<AuthTokenEntity>()

        // JWTs
        JWT_REGEX.findAll(rawContent).forEach { m ->
            found += makeToken(host, m.value, "RAW", "Authorization", true)
        }

        // Bearer tokens
        BEARER_REGEX.findAll(rawContent).forEach { m ->
            val v = m.groupValues[1]
            if (v.length > 16) found += makeToken(host, v, "RAW", "Authorization", true)
        }

        // Authorization header literals in JS
        AUTH_HEADER_REGEX.findAll(rawContent).forEach { m ->
            val v = m.groupValues[1].trim()
            if (v.length > 8) found += makeToken(host, v, "RAW", "Authorization", false)
        }

        // CSRF tokens
        CSRF_REGEX.findAll(rawContent).forEach { m ->
            val v = m.groupValues[1].trim()
            if (v.length >= 16) found += makeToken(host, v, "RAW", "X-CSRFToken", false)
        }

        // Base64 blobs — try to decode and check for JSON credential objects
        BASE64_BLOB.findAll(rawContent).forEach { m ->
            val raw = m.value
            try {
                val decoded = String(Base64.decode(raw, Base64.DEFAULT))
                if (decoded.contains("token") || decoded.contains("auth") || decoded.contains("key")) {
                    found += makeToken(host, raw, "BASE64", "Authorization", false)
                }
            } catch (_: Exception) {}
        }

        // Base44 tokens
        BASE44_REGEX.findAll(rawContent).forEach { m ->
            val raw = m.value
            if (raw.all { it in BASE44_ALPHABET } && raw.length in 24..128) {
                found += makeToken(host, raw, "BASE44", "Authorization", false)
            }
        }

        found.distinctBy { it.id }.forEach { tokenDao.upsert(it) }
    }

    /** Decode a stored token value for injection into a request header. */
    fun decodeForInjection(token: AuthTokenEntity): String {
        val raw = when (token.encoding) {
            "BASE64" -> try { String(Base64.decode(token.value, Base64.DEFAULT)) } catch (_: Exception) { token.value }
            "BASE44" -> decodeBase44(token.value)
            else     -> token.value
        }
        return if (token.isBearer && !raw.startsWith("Bearer ")) "Bearer $raw" else raw
    }

    /** Report a successful use — promotes token to ACTIVE. */
    suspend fun reportSuccess(token: AuthTokenEntity) {
        tokenDao.updateStatus(token.id, "ACTIVE", 1, 0, System.currentTimeMillis())
    }

    /** Report a failed use — marks token FAILED after threshold. */
    suspend fun reportFailure(token: AuthTokenEntity) {
        val newFail = token.failureCount + 1
        val status = if (newFail >= 3) "FAILED" else token.status
        tokenDao.updateStatus(token.id, status, 0, 1, System.currentTimeMillis())
    }

    /** Check if a JWT is expired based on its `exp` claim. */
    fun isJwtExpired(value: String): Boolean {
        return try {
            val parts = value.split(".")
            if (parts.size < 2) return false
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
            val exp = JSONObject(payload).optLong("exp", 0L)
            exp > 0 && exp < System.currentTimeMillis() / 1000
        } catch (_: Exception) { false }
    }

    private fun makeToken(host: String, value: String, encoding: String, header: String, bearer: Boolean): AuthTokenEntity {
        val shortHash = value.take(8).hashCode().toString(16)
        return AuthTokenEntity(
            id = "$host::$shortHash",
            host = host,
            value = value,
            encoding = encoding,
            headerName = header,
            isBearer = bearer
        )
    }

    private fun decodeBase44(input: String): String {
        // Base44 decode: treat each char as a digit in base-44 alphabet
        var num = java.math.BigInteger.ZERO
        val base = java.math.BigInteger.valueOf(44)
        for (ch in input) {
            val idx = BASE44_ALPHABET.indexOf(ch)
            if (idx < 0) return input
            num = num.multiply(base).add(java.math.BigInteger.valueOf(idx.toLong()))
        }
        return num.toString(16).chunked(2).map { it.toInt(16).toChar() }.joinToString("")
    }
}
