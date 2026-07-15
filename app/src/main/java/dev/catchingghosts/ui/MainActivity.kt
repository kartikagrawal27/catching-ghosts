package dev.catchingghosts.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import dev.catchingghosts.R
import dev.catchingghosts.integrity.*

/**
 * One screen. One protected action. A scenario picker standing in for the
 * policy engine. Everything attestation-side is real: warm-up, minting,
 * hashing, error buckets, remediation dialogs.
 */
class MainActivity : AppCompatActivity(), IntegrityClient.OutcomeRenderer {

    // ← change to YOUR cloud project number (Play Console → App integrity)
    private val cloudProjectNumber = 123456789012L

    private val simulator = ScenarioSimulatorBackend()
    private lateinit var client: IntegrityClient
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)

        client = IntegrityClient(applicationContext, cloudProjectNumber, simulator)
        client.initialize { line -> status(line) }   // eager warm-up: gotcha #8

        val spinner = findViewById<Spinner>(R.id.scenario)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            Scenario.entries.map { it.label })
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                simulator.scenario = Scenario.entries[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<MaterialButton>(R.id.send).setOnClickListener {
            val action = ProtectedAction(type = "TRANSFER", amountMinorUnits = 50_000, destinationId = "acct_demo")
            client.attestedCall(action, this, this)
        }
    }

    // ── OutcomeRenderer: the client renders; it never decides ────────────
    override fun proceed() = status("✅ $500 sent. (Outcome: Proceed)")

    override fun stepUp(method: StepUpMethod, onVerified: () -> Unit) {
        when (method) {
            StepUpMethod.BIOMETRIC -> {
                status("step-up: confirm it's you")
                val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = onVerified()
                        override fun onAuthenticationError(code: Int, msg: CharSequence) =
                            status("step-up cancelled: $msg")
                    })
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Confirm it's you")
                    .setSubtitle("Required for this transfer")   // WHAT, never WHY (see docs)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                    .build())
            }
            StepUpMethod.OTP -> status("step-up: OTP screen would launch here")
        }
    }

    override fun blocked(message: String) = status("⛔ $message")
    override fun status(text: String) { status.text = text }
}
