# Catching Ghosts 👻

**The companion repository for "Catching Ghosts: How Play Integrity Stops Devices That Don't Exist"**
droidcon USA 2026 · Orlando · Kartik Agrawal

Everything promised from the stage lives here: the guides, the extended bestiary (including the ghosts that didn't fit in forty minutes), the full gotcha catalog, the diagrams, and a reference Android app that demonstrates the entire client-side integration, including every enforcement outcome, on demand.

## What's here

| Path | What it is |
|---|---|
| [`docs/implementation-guide.md`](docs/implementation-guide.md) | The step-by-step client build guide: warm-up, request hashing, the full round trip, remediation dialogs, the four-bucket error taxonomy. Start here. |
| [`docs/deep-dive.md`](docs/deep-dive.md) | The expert deep dive: hardware attestation and the May 2025 shift, verdict payload anatomy, the 2025-26 signal arsenal (appAccessRisk, recentDeviceActivity, deviceRecall), the policy engine, hard Q&A. |
| [`docs/bestiary.md`](docs/bestiary.md) | All the ghosts: the four from the talk, plus **The Imposter** and **The Sleepwalker**, the ones that didn't fit. |
| [`docs/gotchas.md`](docs/gotchas.md) | The complete gotcha catalog, including the ones that only cost you an outage to learn. |
| [`app/`](app/) | The scenario-simulator Android app (see below). |
| `slides/` | The deck from the talk. (Uploaded after the conference.) |

## The scenario-simulator app

A deliberately small, honest demo: **one screen, one protected action ("Send $500"), and a scenario picker.** The attestation side is real: real provider warm-up at app start, real Standard-request token minting, real request-hash construction, the real four-bucket error handler, and real Play remediation dialogs. The backend is simulated by design: the picker injects the server outcome (Proceed / StepUp / Remediate / Deny) so you can exercise every rendering path on demand without needing a fraud backend, or a fraudster.

Why simulate the backend? Because the client's whole job is to **carry the token and render the outcome, never to decide trust**. This app is that principle, compiled.

### Run it
1. Open `app/` in Android Studio (Hedgehog+).
2. Set your own `applicationId` and `CLOUD_PROJECT_NUMBER` in `IntegrityClient.kt` (from Play Console → App integrity).
3. For clean verdicts, install via a Play internal-testing track; adb debug builds are, by design, unrecognized sideloads (see gotchas #7).
4. Pick a scenario, tap **Send $500**, watch the outcome path run.

### The shape worth stealing
- `IntegrityClient.kt`: one public verb (`attestedCall`), app-scoped provider cache, re-warm on `-19` and after successful remediation. Feature code never touches tokens.
- `RequestHasher.kt`: canonical-string discipline; treat the format as a wire protocol.
- `ServerOutcome.kt` / `Backend.kt`: the outcome vocabulary and the seam where a real backend replaces the simulator.

## The one-line thesis
> Attestation is not a feature you integrate. It's a trust layer you design.

## License
MIT. Take the shapes, ship safer apps.
