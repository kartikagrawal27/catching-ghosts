package dev.catchingghosts.integrity

/**
 * The seam. In production this POSTs { action, integrityToken } to your API,
 * whose policy engine returns an outcome. In this demo, a simulator injects
 * the outcome you picked — because the client's job is to carry the token and
 * render the result, never to contain the decision.
 */
interface Backend {
    fun execute(action: ProtectedAction, integrityToken: String, onOutcome: (ServerOutcome) -> Unit)
}

class ScenarioSimulatorBackend(@Volatile var scenario: Scenario = Scenario.HEALTHY) : Backend {
    override fun execute(action: ProtectedAction, integrityToken: String, onOutcome: (ServerOutcome) -> Unit) {
        // A real backend would: decode the token via Google, verify package +
        // requestHash + freshness, classify tiers, run the policy ladder.
        // Here, the picker has already decided.
        onOutcome(scenario.outcome)
    }
}
