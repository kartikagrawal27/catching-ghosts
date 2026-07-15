package dev.catchingghosts.integrity

import android.app.Activity
import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.model.IntegrityDialogResponseCode
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The one component feature code talks to. One public verb: [attestedCall].
 * Everything else — provider lifecycle, hashing, error buckets, remediation
 * dialogs — is machinery that never leaks out.
 *
 * Lifecycle rules encoded here (each learned the hard way, see docs/gotchas.md):
 *  · ONE provider per process, warmed at app start        (gotcha #8 🔥)
 *  · re-warm ONLY on -19 or after a successful dialog     (gotchas #9, #25 🔥)
 *  · the client renders outcomes; it never decides trust  (gotcha #30 🔥)
 */
class IntegrityClient(
    appContext: Context,
    private val cloudProjectNumber: Long,   // ← YOUR number from Play Console
    private val backend: Backend
) {
    interface OutcomeRenderer {
        fun proceed()
        fun stepUp(method: StepUpMethod, onVerified: () -> Unit)
        fun blocked(message: String)
        fun status(text: String)
    }

    private val manager: StandardIntegrityManager = IntegrityManagerFactory.createStandard(appContext)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var provider: StandardIntegrityTokenProvider? = null

    // ── lifecycle ─────────────────────────────────────────────────────────
    fun initialize(onStatus: (String) -> Unit = {}) =
        warmUp(onReady = { onStatus("provider ready") }, onFailure = { onStatus("warm-up failed: ${it.message}") })

    private fun warmUp(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build()
        )
            .addOnSuccessListener { provider = it; onReady() }
            .addOnFailureListener { onFailure(it as? Exception ?: RuntimeException(it)) }
    }

    // ── the one public verb ───────────────────────────────────────────────
    fun attestedCall(action: ProtectedAction, activity: Activity, ui: OutcomeRenderer, attempt: Int = 0) {
        val p = provider ?: run {
            ui.status("warming provider…")
            warmUp(onReady = { attestedCall(action, activity, ui, attempt) },
                   onFailure = { ui.blocked("Couldn't verify this device. Try again.") })
            return
        }
        ui.status("minting token…")
        p.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(RequestHasher.forAction(action))
                .build()
        )
            .addOnSuccessListener { tokenResponse -> submit(action, tokenResponse, activity, ui) }
            .addOnFailureListener { e -> onTokenError(e, attempt, action, activity, ui) }
    }

    // ── deliver to the judge, render the verdict ─────────────────────────
    private fun submit(
        action: ProtectedAction,
        tokenResponse: StandardIntegrityToken,
        activity: Activity,
        ui: OutcomeRenderer
    ) {
        ui.status("token minted · asking the judge…")
        backend.execute(action, tokenResponse.token()) { outcome ->
            when (outcome) {
                is ServerOutcome.Proceed -> ui.proceed()
                is ServerOutcome.StepUp -> ui.stepUp(outcome.method) { ui.proceed() }
                is ServerOutcome.Deny -> ui.blocked(outcome.userMessage)
                is ServerOutcome.Remediate -> showRemediation(outcome.dialogTypeCode, tokenResponse, action, activity, ui)
            }
        }
    }

    private fun showRemediation(
        dialogTypeCode: Int,
        tokenResponse: StandardIntegrityToken,
        action: ProtectedAction,
        activity: Activity,
        ui: OutcomeRenderer
    ) {
        ui.status("remediation: Play is rendering the fix…")
        // One showDialog per response object (gotcha #24). API surface note:
        // on 1.5.x this is exposed via the token response; check the current
        // javadoc if this call moves between minor versions.
        tokenResponse.showDialog(activity, dialogTypeCode)
            .addOnSuccessListener { code ->
                when (code) {
                    IntegrityDialogResponseCode.DIALOG_SUCCESSFUL -> {
                        // The world changed; the snapshot didn't (gotcha #25 🔥):
                        provider = null
                        ui.status("fixed · re-attesting with a fresh provider…")
                        attestedCall(action, activity, ui)
                    }
                    IntegrityDialogResponseCode.DIALOG_CANCELLED ->
                        ui.blocked("Action unavailable until the issue is resolved.")
                    else -> ui.blocked("Couldn't complete the fix. Try again later.")
                }
            }
            .addOnFailureListener { ui.blocked("Couldn't show the fix dialog.") }
    }

    // ── the four buckets (gotcha #27) ─────────────────────────────────────
    private fun onTokenError(
        e: Exception, attempt: Int, action: ProtectedAction, activity: Activity, ui: OutcomeRenderer
    ) {
        val ex = e as? StandardIntegrityException ?: run {
            ui.blocked("Couldn't verify this device."); return
        }
        when (ex.errorCode) {
            // 1 · transient → backoff (5s, 10s, 20s; cap 3)
            StandardIntegrityErrorCode.NETWORK_ERROR,
            StandardIntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE,
            StandardIntegrityErrorCode.CLIENT_TRANSIENT_ERROR -> {
                if (attempt < 3) {
                    val delay = 5L shl attempt   // 5, 10, 20
                    ui.status("transient error · retrying in ${delay}s (attempt ${attempt + 1}/3)")
                    scheduler.schedule({
                        activity.runOnUiThread { attestedCall(action, activity, ui, attempt + 1) }
                    }, delay, TimeUnit.SECONDS)
                } else ui.blocked("Network trouble. Try again in a moment.")
            }
            // transient-with-a-lesson: you are the problem (gotcha #8 🔥)
            StandardIntegrityErrorCode.TOO_MANY_REQUESTS ->
                ui.blocked("Too many checks right now — try again shortly. (And audit your call rate.)")
            // 2 · remediable → let Play fix the environment
            StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
            StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
            StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
            StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED ->
                ui.blocked("Please update Google Play services / Play Store and retry.")
            // 3 · provider-stale → re-warm once (gotcha #9)
            StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID -> {
                provider = null
                if (attempt < 1) attestedCall(action, activity, ui, attempt + 1)
                else ui.blocked("Couldn't verify this device.")
            }
            // 4 · terminal → fix your build; do not retry
            else -> ui.blocked("Verification unavailable (code ${ex.errorCode}). See gotchas.md §errors.")
        }
    }
}
