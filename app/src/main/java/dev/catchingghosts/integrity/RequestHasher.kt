package dev.catchingghosts.integrity

import android.util.Base64
import java.security.MessageDigest

/**
 * The client-side half of server trust. The canonical format below is a WIRE
 * PROTOCOL: the server must rebuild the identical string from the request it
 * actually receives. Document it. Version it. Golden-test it on both sides.
 *
 * The day the two builders drift, one of two things happens: legitimate
 * requests fail mysteriously, or someone under deadline pressure downgrades
 * mismatches from reject to log-and-allow — and the binding is decorative.
 */
object RequestHasher {

    // v1 canonical format: type|amountMinorUnits|destinationId|idempotencyKey
    fun forAction(action: ProtectedAction): String {
        val canonical = buildString {
            append(action.type)
            append('|'); append(action.amountMinorUnits)
            append('|'); append(action.destinationId)
            append('|'); append(action.idempotencyKey)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        // ≤ 500 chars or REQUEST_HASH_TOO_LONG; never put PII in the canonical string
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
