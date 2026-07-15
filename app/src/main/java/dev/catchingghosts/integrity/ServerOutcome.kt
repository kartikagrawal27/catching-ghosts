package dev.catchingghosts.integrity

import com.google.android.play.core.integrity.model.IntegrityDialogTypeCode

/**
 * The judge's vocabulary. Note what is ABSENT: reasons. The client (and
 * therefore an attacker running the client in a debugger) never learns which
 * signal failed. ShadowFlag is also absent by design — its wire response is
 * indistinguishable from Proceed.
 */
sealed interface ServerOutcome {
    data object Proceed : ServerOutcome
    data class StepUp(val method: StepUpMethod) : ServerOutcome
    data class Remediate(val dialogTypeCode: Int) : ServerOutcome
    data class Deny(val userMessage: String) : ServerOutcome
}

enum class StepUpMethod { BIOMETRIC, OTP }

/** Scenarios the simulator can inject — one per ghost, plus the happy path. */
enum class Scenario(val label: String, val outcome: ServerOutcome) {
    HEALTHY("Healthy device → Proceed", ServerOutcome.Proceed),
    HOLLOW("The Hollow: weak device → Remediate (GET_INTEGRITY)",
        ServerOutcome.Remediate(IntegrityDialogTypeCode.GET_INTEGRITY)),
    PICKPOCKET("The Pickpocket: binding mismatch → Deny",
        ServerOutcome.Deny("This action isn't available right now.")),
    FARMHAND("The Farmhand: LEVEL_4 rhythm → Deny (real life: ShadowFlag)",
        ServerOutcome.Deny("This action isn't available right now.")),
    PUPPETEER("The Puppeteer: screen-capture risk → Remediate (CLOSE_ALL_ACCESS_RISK)",
        ServerOutcome.Remediate(IntegrityDialogTypeCode.CLOSE_ALL_ACCESS_RISK)),
    AGING_PHONE("Aging phone, BASIC only → StepUp (biometric)",
        ServerOutcome.StepUp(StepUpMethod.BIOMETRIC)),
}
