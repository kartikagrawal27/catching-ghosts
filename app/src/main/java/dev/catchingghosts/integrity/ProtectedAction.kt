package dev.catchingghosts.integrity

import java.util.UUID

/**
 * The semantics of the thing being protected. The requestHash is derived from
 * EXACTLY these fields, in a canonical order the server can recompute.
 */
data class ProtectedAction(
    val type: String,               // e.g. "TRANSFER"
    val amountMinorUnits: Long,     // 50000 = $500.00
    val destinationId: String,
    val idempotencyKey: String = UUID.randomUUID().toString()
)
