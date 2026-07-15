# Play Integrity: The Expert Deep Dive
### Everything beyond the article, prepared for Droidcon USA 2026

Your article covered: Classic vs Standard selection, client integration, nonce hygiene, retry/backoff, quotas, and the gotcha checklist. This document covers everything else, organized from the silicon up to the policy layer. Master this and you can field any question in the room.

---

## Part 0: The Bridge. Your World Then, The World Now

You built the Cash App platform through the SafetyNet deprecation and into the early Play Integrity era, and you published the article in September 2025. Here is the explicit mapping from what you knew and did then to what has changed since, so every later section of this doc has a mental slot to land in.

| What you knew / did then | What's true now | Where in this doc |
|---|---|---|
| Device verdicts were ultimately judged from a large basket of signals collected on-device and evaluated on Google's servers. Root-hiding tools fought those signals, and often won. | Since May 2025, on Android 13+ all three device tiers are anchored in hardware key attestation: the secure chip signs the testimony, and the OS can no longer lie on its own behalf. Signal collection dropped ~90%, latency improved up to ~80%. | Part 1.1, 1.2 |
| MEETS_STRONG_INTEGRITY meant hardware-backed trust, full stop. | STRONG now also requires a security patch from the last 12 months. Healthy older devices silently fall out of STRONG when their manufacturer stops updating them. If your Cash App tiering treated STRONG as stable device property, that assumption broke in May 2025. | Part 1.2 |
| Verdicts arrived regardless of how the app got on the device. | On Android 13+, full verdicts require the app to have been installed or updated by Google Play. Sideloads and some enterprise provisioning flows lost verdict coverage. | Part 1.2 |
| The core payload was four fields: requestDetails, appIntegrity, deviceIntegrity, accountDetails. | The payload grew: environmentDetails (appAccessRisk, playProtect), recentDeviceActivity, deviceAttributes, and deviceRecall (beta). The API's question expanded from "is this device real" to "is this session safe" and "have I seen this device sin before." | Parts 2, 4 |
| When attestation failed, you built your own UX: retry taxonomies, error messaging, "please update Play services" screens. Your article's guidance was retry transient errors, fail fast on the rest with user messaging. | Retry taxonomy still correct and still yours. But the "fail fast with user messaging" half now has an official replacement: Play remediation dialogs (library 1.5.0, Aug 2025). Play itself renders the fix flow for licensing, tampered-binary, weak-integrity, and remediable-error cases. Enforcement got cheaper because recovery got easier. | Part 5 |
| Token farming and replay were threats you defended with nonce/requestHash binding and backend heuristics. | Binding is still the backbone, unchanged. But Google added first-class counters: recentDeviceActivity buckets token-minting volume per device per hour, and deviceRecall gives you fraud memory that survives factory reset. The bespoke systems you built now have platform-native equivalents. | Parts 4.3, 4.4 |
| The bypass economy attacked software signals (MagiskHide-style). | The bypass economy now attacks hardware trust: leaked OEM keyboxes presented as false device identity, revoked by Google in a rolling arms race. | Part 1.4 |
| Classic vs Standard as you wrote it: Classic for infrequent high-confidence checks, Standard for frequent low-latency ones. | Still exactly right. Two footnotes: library 1.5.0 raised minSdk to 23 for both modes, and the ecosystem guidance has hardened further toward Standard-by-default. | unchanged |
| Nonce rules, backoff shape (5s → 10s → 20s, ~3 attempts), quota discipline, don't-cache-Classic-verdicts. | All still correct as written. Your article did not age out; the ground floor held. The building just got taller. | unchanged |

The one-sentence version you can carry on stage: **everything you wrote about how to talk to the API is still true; what changed is what the API's answer is made of, and what you can do with it.**

---


## Part 1: The Trust Chain Under the Hood

### 1.1 Why "hardware-backed" is the whole story now

Your article treated the verdict as a signed statement from Google. The expert-level question is: what is Google's statement actually based on? The answer since May 2025 is Android Platform Key Attestation, and understanding it is what separates "I integrated the API" from "I understand attestation."

The chain works like this:

1. Every certified Android device ships with an attestation key provisioned into its secure hardware at the factory (or provisioned remotely via Remote Key Provisioning on newer devices). This lives in the TEE (Trusted Execution Environment) or, on higher-end devices, a StrongBox secure element (a physically separate tamper-resistant chip, e.g. Titan M on Pixels).
2. That key chains up to a Google hardware attestation root certificate. The private keys never leave the secure hardware.
3. When an app (or Play services on the app's behalf) asks the hardware to attest, the secure hardware signs a certificate chain that includes claims about the device: verified boot state, bootloader lock state, OS patch level, and the key's security level (TEE vs StrongBox).
4. The critical property: the Android OS itself cannot forge this. Even a fully rooted device with a compromised kernel cannot make the secure element sign a false statement, because the signing happens below the OS.

Before May 2025, Play Integrity's device verdicts leaned heavily on a large basket of software-collected signals evaluated on Google's servers. The December 2024 announcement (enforced May 2025) rebuilt the verdicts around key attestation as the backbone. Google's stated numbers: ~90% reduction in device signals collected, and verdict latency improvements of up to ~80%. The security consequence is bigger than the performance one: spoofing software signals was a cat-and-mouse game attackers were winning; forging a hardware signature requires extracting keys from a secure element.

**The talk-ready one-liner:** "Before May 2025, the device's operating system testified about itself. After May 2025, the chip testifies about the operating system."

### 1.2 Exactly what changed in May 2025

Announced December 2024, opt-in immediately, automatic transition for all integrations in May 2025 with no developer work required. On devices running Android 13 (API 33) and higher:

- **All three device tiers now use hardware-backed signals.** MEETS_BASIC_INTEGRITY, MEETS_DEVICE_INTEGRITY, and MEETS_STRONG_INTEGRITY all derive from Android Platform Key Attestation rather than the old software signal basket.
- **MEETS_STRONG_INTEGRITY now has a freshness requirement tied to the device's security patch level.** Every Android device shows a "security patch date" in Settings, which reflects the last monthly security update its manufacturer shipped to it. Post-May-2025, a device only earns the STRONG label if that patch date is less than 12 months old. The consequence: a device can lose STRONG without the user changing anything. Say someone bought a flagship in 2021 and the manufacturer ended update support in 2024. Their phone works fine, is not rooted, and passed STRONG for years, but once its last patch is more than a year stale, it silently drops to DEVICE-only. Google's reasoning is that STRONG is meant to certify a device against current threats, and an unpatched device carries known, unfixed vulnerabilities. Why this matters most for fintech-style apps: they are the ones most likely to gate sensitive actions on STRONG, so aging-but-legitimate devices in their user base started failing those gates in May 2025 through no fault of the user.
- **Apps must have been installed or updated by Google Play to receive full verdicts.** Sideloaded installs and some enterprise provisioning flows (QR/NFC/zero-touch enrolled devices) stopped receiving the enhanced verdicts, which caught the MDM/EMM world off guard (Intune had to publish migration guidance and pushed its own enforcement to September 30, 2025).
- **Google adjusts verdicts dynamically when it detects ecosystem-level threats,** such as evidence of key compromise or anomalous activity on a device model or SDK version, without developer action.
- **Verdict content was standardized** across apps, games, and SDKs for consistency.

On Android 12 and lower, the legacy definitions still apply (DEVICE uses hardware-backed attestation where available with software fallback). This forked-definition reality is why `deviceAttributes.sdkVersion` exists (Part 2.4): your backend can apply different trust weights to a MEETS_STRONG_INTEGRITY from an Android 14 device (post-2025 definition, patched within 12 months) vs an Android 12 device (legacy definition).

**Fleet impact you should be ready to discuss:** the loudest fallout was legitimate power users. Rooted devices and custom ROMs that previously scraped by via spoofing modules (Play Integrity Fix and friends) began failing DEVICE and STRONG consistently. Older devices out of patch support silently lost STRONG. Any app that had hard-coded "block below STRONG" enforcement saw false positives spike in May 2025. This is a perfect on-stage argument for tiered enforcement and observe-before-enforce.

### 1.3 The February 2026 root certificate rotation

Google rotated the Android Platform root attestation certificate in February 2026. Key facts:

- **Developers who directly implement key attestation had to update their trust stores or their validation would start rejecting legitimate new chains.** To unpack that: Play Integrity is not the only way to do attestation. A team can skip the API entirely and use the raw platform primitive underneath it. That looks like this: the app asks Android KeyStore to generate a key inside the secure hardware with an attestation challenge attached (`setAttestationChallenge()` on the KeyGenParameterSpec), the hardware returns an X.509 certificate chain for that key, the app ships the chain to the team's own backend, and the backend validates it: verify each certificate signs the next, parse the attestation extension for the device claims (boot state, patch level, etc.), and, critically, check that the chain terminates at one of Google's published hardware attestation root certificates. That last step is the "trust store": a hardcoded or configured list of root certs your server considers legitimate. The February 2026 rotation means Google began issuing device attestation chains that terminate at a *new* root certificate. Any backend whose trust store only contained the old root would look at a perfectly genuine chain from a perfectly genuine device, fail to find its root in the trusted list, and reject it as untrusted. From the outside this looks like a sudden spike in "attestation failures" from healthy devices. The fix is operational, not cryptographic: add the new root to the trust store before rotated chains start arriving, and keep the old root during the overlap period since both old and new chains coexist in the fleet for a long time.
- Developers using **Play Integrity API needed to do nothing.** Google absorbs root rotations, device-model key issues, and OEM provisioning outages behind the API.

This is one of your strongest "why use Play Integrity instead of raw key attestation" arguments: the API is an abstraction layer over a messy, rotating, occasionally-compromised hardware PKI. Google mitigates leaked OEM keyboxes and root rotations centrally; a DIY key attestation implementation inherits all of that operational burden.

### 1.4 The attacker landscape (know your ghosts)

You will present as a security expert, so you need fluency in how the defenses have historically been challenged. Keep this at the conceptual level on stage (never a how-to), but know the mechanics:

- **Software signal spoofing (the pre-2025 era).** Root-hiding frameworks intercepted the signals SafetyNet and early Play Integrity collected and fed back clean values. This is why the DEVICE verdict used to be passable on rooted phones and why May 2025 happened.
- **Keybox leakage (the current era).** With hardware attestation as the backbone, the main bypass economy shifted to leaked OEM attestation keys ("keyboxes") extracted from devices or leaked from supply chains. Spoofing tools inject a leaked-but-valid keybox so the device presents someone else's hardware identity. Google's countermeasure is revocation: leaked keyboxes get banned, which is why the bypass community reports STRONG "breaking every few weeks." This arms race dynamic is great stage material: the defense doesn't have to be unbreakable, it has to be more expensive to break than the fraud is worth.
- **Token farming.** Attackers run real, certified, untouched devices whose only job is to generate valid integrity tokens, then distribute those tokens to bots running on emulators. The token is genuine; the client presenting it is not. Defenses: request binding (nonce/requestHash, which you covered), token freshness windows, and recentDeviceActivity (Part 4.3), which explicitly exists to catch devices minting anomalous token volumes.
- **Replay.** Capturing a legitimate token and re-submitting it. Defeated by request binding plus server-side timestamp checks (Part 3.4).
- **The unprotectable client truth.** No client-side check can be trusted on its own; the client binary is in the attacker's hands. Attestation's entire value is that the *verdict is produced and verified off-device*. The client is a courier, never a judge.

---

## Part 2: Verdict Payload Anatomy (the full read)

Your article stayed client-side. As the expert on stage, you should be able to sketch the entire decrypted payload from memory and explain what each field is for and how it fails.

### 2.1 The complete payload shape

```json
{
  "requestDetails": {
    "requestPackageName": "com.example.app",
    "requestHash": "aGVsbG8gd29ybGQgdGhlcmU",     // standard requests
    "nonce": "...",                                // classic requests instead
    "timestampMillis": "1719858000000"
  },
  "appIntegrity": {
    "appRecognitionVerdict": "PLAY_RECOGNIZED",
    "packageName": "com.example.app",
    "certificateSha256Digest": ["6a6a1474b5cbd..."],
    "versionCode": "42"
  },
  "deviceIntegrity": {
    "deviceRecognitionVerdict": [
      "MEETS_BASIC_INTEGRITY",
      "MEETS_DEVICE_INTEGRITY",
      "MEETS_STRONG_INTEGRITY"
    ],
    "deviceAttributes": { "sdkVersion": 34 },
    "recentDeviceActivity": { "deviceActivityLevel": "LEVEL_1" },
    "deviceRecall": {
      "values": { "bitFirst": true, "bitSecond": false, "bitThird": false },
      "writeDates": { "yyyymmFirst": 202605 }
    }
  },
  "accountDetails": {
    "appLicensingVerdict": "LICENSED"
  },
  "environmentDetails": {
    "appAccessRiskVerdict": { "appsDetected": ["KNOWN_INSTALLED"] },
    "playProtectVerdict": "NO_ISSUES"
  }
}
```

Fields beyond the core four (requestDetails, appIntegrity, deviceIntegrity, accountDetails) appear only when opted in via Play Console → Protected with Play → Play Integrity API → Responses. Turning device recall on or off deletes any configured test responses, a nasty little operational gotcha.

### 2.2 The order of verification matters

Google's own guidance now codifies what you built at Cash App: check the baseline before you even look at device trust.

1. **requestDetails first, always.** Verify `requestPackageName` matches your package, `requestHash`/`nonce` matches what this specific server request should carry, and `timestampMillis` is within your freshness window. Skipping this makes every other field meaningless because the whole token could be replayed.
2. **appIntegrity next.** `PLAY_RECOGNIZED` means the binary and signing cert match what Play knows. `UNRECOGNIZED_VERSION` = tampered, resigned, or sideloaded-unknown build. `UNEVALUATED` = evaluation could not happen (device below Android 6, unknown app version, outdated Play Store, etc.). Also verify `certificateSha256Digest` against your own release cert digest, not just the verdict string; this closes the loop if Play's recognition is ever confused by a rollout edge case.
3. **accountDetails.** `LICENSED` = installed/paid via Play by this account. `UNLICENSED` = sideloaded or different store. `UNEVALUATED` = couldn't tell. Games and paid apps weight this heavily; free apps often treat UNLICENSED as a soft signal.
4. **Only then deviceIntegrity and environmentDetails**, which feed your risk tiering rather than gating outright.

### 2.3 Device tiers: precise definitions post-May-2025

- **MEETS_STRONG_INTEGRITY**: hardware-backed proof of untampered, certified device, verified boot, AND (Android 13+) a security patch released within the last 12 months. The population holding STRONG is materially smaller than DEVICE. Never make STRONG your floor for general access; reserve it for step-up on your most sensitive actions (large transfers, credential export).
- **MEETS_DEVICE_INTEGRITY**: hardware-backed, Play Protect certified, verified boot, no patch recency requirement. This is the sane default floor for sensitive-but-normal actions.
- **MEETS_BASIC_INTEGRITY**: passes basic checks; bootloader may be unlocked, boot state may be unverified, device may be uncertified. On Android 13+ it requires only that the attestation root of trust is Google-provided. Rooted-but-honest devices commonly land here. Treat as "real hardware, low trust."
- **MEETS_VIRTUAL_INTEGRITY**: the app runs in a Google-blessed emulator with Play services (primarily Google Play Games for PC) that passes its own integrity checks. If you don't ship to Play Games for PC you will essentially never see it, but knowing it exists signals depth.
- **Empty verdict**: worse than BASIC. No label at all means Play couldn't establish anything: emulator, unknown ROM, API below 23, or active tampering.

Labels are cumulative: a healthy flagship returns all three of BASIC/DEVICE/STRONG. Your server logic should therefore be "contains X," never "equals X."

### 2.4 deviceAttributes: the SDK-version disambiguator

```json
"deviceIntegrity": {
  "deviceRecognitionVerdict": ["MEETS_DEVICE_INTEGRITY", "MEETS_STRONG_INTEGRITY"],
  "deviceAttributes": { "sdkVersion": 34 }
}
```

Opt-in field carrying the *attested* Android SDK version (not the spoofable `Build.VERSION.SDK_INT` your client reports). Two expert uses:

- Apply different trust weights to STRONG depending on whether the device is on the post-May-2025 definition (sdkVersion >= 33) or the legacy one (<= 32).
- Cross-check against what the client claimed about itself. A client reporting Android 14 whose attested sdkVersion says 12 is lying about something.

If evaluation fails, the field is empty. Treat empty as UNEVALUATED, not as innocent.


---

## Part 3: What Happens Server-Side (the five-minute version)

This is an Android conference, so you only need the shape of the backend story, not its plumbing. Here is the 20% that lets you speak confidently about the other side of the wire.

### 3.1 The token's journey after it leaves your app

The client never sees inside the token. It's an opaque, encrypted blob; your app is a courier. The backend turns it into a decision in three steps:

1. **Decrypt and verify.** By default the backend sends the token to Google's `decodeIntegrityToken` endpoint and gets back the plaintext JSON verdict (the payload you saw in Part 2). There is an alternative where teams download keys from Play Console and decrypt Classic-request tokens locally on their own servers: faster (no Google round trip) and outage-resistant, but they take on key custody and lose Google's server-side anti-abuse evaluation. Standard requests are Google-decrypted only. That's the entire decryption story you need.
2. **Validate the binding.** Before trusting any verdict field, the server confirms the token belongs to *this* request from *this* app: package name matches, the requestHash/nonce matches what this action should carry, and the token's timestamp is fresh (minutes, not hours). This ordering is the whole anti-replay defense; a stolen token fails the hash check, an old token fails the freshness check, and a re-submitted token can be caught by keeping a short-lived "already seen" record.
3. **Classify, then decide.** Read verdicts in order: appIntegrity (is this our binary, does the signing cert digest match ours), accountDetails (licensed?), then deviceIntegrity mapped to a trust tier. The tier feeds the policy engine (Part 6). One server-side subtlety worth quoting on stage: labels are cumulative, so correct code checks "contains MEETS_DEVICE_INTEGRITY," never "equals."

That's it. Everything else about the server is implementation detail your backend colleagues own.

### 3.2 Request hash construction: the client-side half of server trust

This one is yours, because the requestHash is computed *on the client*, and getting it right is what makes step 2 above work. The hash must be a digest of the semantics of the protected action, so the token is welded to what the request actually does:

```kotlin
// CLIENT: bind the token to what the request actually does
fun requestHashFor(action: ProtectedAction): String {
    val canonical = buildString {
        append(action.type)            // e.g. "TRANSFER"
        append('|'); append(action.amountMinorUnits)
        append('|'); append(action.destinationId)
        append('|'); append(action.idempotencyKey)
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
```

The server recomputes the same hash from the request body it received and compares. If an attacker steals a valid token minted for a $5 transfer and attaches it to a $5,000 transfer, the hash no longer matches. Two constraints worth knowing cold: requestHash max length is 500 characters (REQUEST_HASH_TOO_LONG error otherwise), and unlike the Classic nonce there is no server round trip to obtain it, which is precisely why Standard is lower latency.

---

## Part 4: The Environment Signals (the 2025-2026 arsenal)

These are the opt-in signals your article's closing sentence gestured at. They are where the API stopped being "is this device real" and became "is this session safe."

### 4.1 appAccessRiskVerdict: who else is in the room

Detects other apps on the device that could capture the screen, draw overlays, or control the device (typically via accessibility-permission misuse). This targets a scam pattern that exploded in banking: a victim installs a "support" app, and a human or script watches or drives their session remotely.

```json
"environmentDetails": {
  "appAccessRiskVerdict": {
    "appsDetected": [
      "KNOWN_INSTALLED",        // Play-known apps installed
      "UNKNOWN_INSTALLED",      // non-Play / unknown apps installed
      "KNOWN_CAPTURING",        // a Play-known app is capturing the screen NOW
      "UNKNOWN_CAPTURING",      // an unknown app is capturing NOW
      "KNOWN_CONTROLLING",      // a Play-known app can control the device
      "UNKNOWN_CONTROLLING"     // an unknown app can control the device
    ]
  }
}
```

Expert details that most people miss:

- Verified accessibility tools (screen readers etc. that passed Google's enhanced accessibility review) are automatically excluded, so enforcing on CAPTURING/CONTROLLING does not lock out legitimate assistive-tech users. This is the answer to the inevitable accessibility question from the audience.
- Requires Integrity library 1.4.0+ for Standard requests; earlier libraries return UNEVALUATED.
- You can opt out per request when the check is irrelevant (e.g. a low-risk read):

```kotlin
val tokenRequest = StandardIntegrityTokenRequest.builder()
    .setRequestHash(requestHash)
    .setVerdictOptOut(setOf(IntegrityTokenRequest.VerdictOptOut.APP_ACCESS_RISK))
    .build()
```

- Enforcement pattern: on `*_CONTROLLING` during a sensitive flow, block the action and trigger the CLOSE_ALL_ACCESS_RISK remediation dialog (Part 5) so Play itself prompts the user to shut the risky apps, rather than you building that UI.

### 4.2 playProtectVerdict: known malware on board

```json
"environmentDetails": { "playProtectVerdict": "NO_ISSUES" }
```

Possible values and their reading:

- `NO_ISSUES`: Play Protect on, nothing found.
- `NO_DATA`: Play Protect hasn't scanned recently; neutral.
- `POSSIBLE_RISK`: Play Protect is off or hasn't been able to run; treat as elevated caution, prompt user to enable.
- `MEDIUM_RISK` / `HIGH_RISK`: Play Protect found unwanted or dangerous apps installed. For fintech, HIGH_RISK during a payment flow is a strong step-up or block trigger.
- `UNEVALUATED`: signal not available for this request.

The nuance: this is a device-hygiene signal, not a session signal. A HIGH_RISK verdict doesn't mean the malicious app is attacking *your* session (that's appAccessRisk's job); it means the device has a known-bad resident. Weight accordingly in the policy engine.

### 4.3 recentDeviceActivity: catching the token farm

The direct counter to token farming. Reports how many integrity tokens *your app* requested *on this device* in the last hour, bucketed:

```json
"deviceIntegrity": {
  "recentDeviceActivity": { "deviceActivityLevel": "LEVEL_2" }
}
```

- LEVEL_1: roughly 10 or fewer tokens in the last hour (normal humans live here)
- LEVEL_2: roughly 11-25
- LEVEL_3: roughly 26-50
- LEVEL_4: more than 50 (nobody taps "transfer" 51 times an hour)

Reading it like an operator: first, baseline your fleet before enforcing, because your own architecture determines what's normal (an app attesting every session start has a different LEVEL_1/LEVEL_2 split than one attesting only on payments). Second, LEVEL_4 devices minting valid tokens are exactly the "real device feeding bots" pattern; the right response is usually rate-limiting or shadow-flagging the *account and device pair* server-side, not an in-app block that tips off the farm operator. Third, combine with device recall (below): flag the farm device once, recognize it forever.

### 4.4 deviceRecall (beta): memory that survives factory reset

The newest and, for fraud platforms, most strategically interesting signal. Three developer-defined boolean bits stored *on Google's servers, keyed to the physical device*, readable and writable by your backend. They survive app reinstall AND factory reset, which defeats the classic fraud loop of reset → new account → repeat abuse.

Reading (arrives in the verdict once enabled):

```json
"deviceIntegrity": {
  "deviceRecognitionVerdict": ["MEETS_DEVICE_INTEGRITY"],
  "deviceRecall": {
    "values": { "bitFirst": true, "bitSecond": false, "bitThird": false },
    "writeDates": { "yyyymmFirst": 202605 }   // YYYYMM UTC; absent for false bits
  }
}
```

Writing is a backend concern, and the high-level shape is all you need: after your server verifies a token from a device, it can call a dedicated Google endpoint (`deviceRecall:write`) to set or clear bits for that device, using that verified token as proof it's talking about the right hardware.

The expert-level mechanics (high level; the write side is backend-owned):

- Your backend has up to 14 days after verifying a token to use it for a recall write, and the write succeeds only if the recall bits inside that token are still current, which prevents two servers from clobbering each other.
- Propagation delay between a write and readability in the next verdict is up to ~30 seconds.
- Bits persist for 3 years after last read/write. Writing false clears the bit and its date.
- All apps under your Play developer account share the same three bits per device: a cross-app reputation system for free, and a footgun if two teams assign different meanings to bitFirst. Governance matters.
- Write requests have separate rate limits and don't consume token quota.
- Semantics are yours. Three independent flags ("confirmed fraud," "trial redeemed," "chargeback history"), or treat the three bits as a 3-bit value for up to 8 device states. writeDates let you decay old judgments, because devices get resold; a fraud bit from 2026 shouldn't damn a refurbished phone's new owner in 2029.
- Beta caveats: interest form + approval required, Play Console toggle wipes your configured test responses, and the API surface may change. Say "beta" on stage.

**Privacy positioning for the talk:** recall bits are not a device fingerprint you compute; they're a narrow, developer-defined, user-resettable-by-you memory hosted by Google, designed as the privacy-preserving alternative to the hardware-ID tracking Android spent a decade removing. Expect a privacy question; this is the answer.

---

## Part 5: Remediation Dialogs (Play fixes your users for you)

Shipped in Integrity library 1.5.0 (August 2025). Before this, a failed verdict left you writing bespoke "please update Play services" UX and hoping. Now Play itself renders a guided fix flow inside your app. This materially changes the enforcement calculus: blocking is less costly when a one-tap recovery path exists.

### 5.1 The dialog catalog

- **GET_INTEGRITY**: the generalist. Detects and guides the fix for unlicensed installs, unrecognized app versions, weak device integrity, and remediable client errors (network connectivity, outdated/missing Play services, outdated Play Store).
- **GET_STRONG_INTEGRITY**: everything GET_INTEGRITY does, plus drives toward MEETS_STRONG_INTEGRITY and a clean playProtectVerdict. Your step-up flow's best friend.
- **GET_LICENSED**: focused recovery to a Play-licensed, Play-recognized state (the original 1.1.0-era dialog, still useful when licensing is the only problem).
- **CLOSE_UNKNOWN_ACCESS_RISK / CLOSE_ALL_ACCESS_RISK**: prompt the user to close capturing/controlling apps flagged by appAccessRiskVerdict (unknown-only vs all).

### 5.2 Triggering on a verdict problem (server-driven)

The canonical loop: client attests → server evaluates → server responds "remediate with dialog X" → client shows it → client re-attests.

```kotlin
// Client side, Standard API, library 1.5.0+
fun handleServerDecision(decision: ServerDecision, activity: Activity) {
    when (decision) {
        is ServerDecision.Proceed -> proceedWithAction()
        is ServerDecision.Remediate -> {
            val dialogRequest = StandardIntegrityDialogRequest.builder()
                .setActivity(activity)
                .setType(decision.dialogTypeCode)     // e.g. IntegrityDialogTypeCode.GET_INTEGRITY
                .setStandardIntegrityResponse(decision.originalResponse)
                .build()

            standardIntegrityManager.showDialog(dialogRequest)
                .addOnSuccessListener { responseCode ->
                    when (responseCode) {
                        IntegrityDialogResponseCode.DIALOG_SUCCESSFUL -> {
                            // User completed the fix. Warm up a NEW provider and re-attest:
                            // the old verdict is stale by definition now.
                            prepareProviderAndRetry()
                        }
                        IntegrityDialogResponseCode.DIALOG_CANCELLED -> showBlockedState()
                        IntegrityDialogResponseCode.DIALOG_FAILED -> showBlockedState()
                        IntegrityDialogResponseCode.DIALOG_UNAVAILABLE -> fallbackMessaging()
                    }
                }
        }
        is ServerDecision.Deny -> showBlockedState()
    }
}
```

### 5.3 Triggering on a client exception

Remediable exceptions (NETWORK_ERROR, PLAY_SERVICES_VERSION_OUTDATED, PLAY_SERVICES_NOT_FOUND, PLAY_STORE_VERSION_OUTDATED, ...) can be handed straight to the dialog instead of your own retry-then-give-up UX:

```kotlin
integrityTokenProvider.request(tokenRequest)
    .addOnFailureListener { e ->
        val ex = e as? StandardIntegrityException ?: return@addOnFailureListener
        if (ex.isRemediable) {
            val req = StandardIntegrityDialogRequest.builder()
                .setActivity(activity)
                .setType(IntegrityDialogTypeCode.GET_INTEGRITY)
                .setStandardIntegrityResponse(ExceptionDetails(ex))
                .build()
            standardIntegrityManager.showDialog(req)
                .addOnSuccessListener { code -> if (code == DIALOG_SUCCESSFUL) retryAttestation() }
        } else {
            applyNonRemediableErrorPolicy(ex.errorCode)   // your article's retry taxonomy
        }
    }
```

Notes with expert texture: `showDialog` can be called once per response object; after DIALOG_SUCCESSFUL on a Standard flow you must warm up the provider again because the cached attestation state changed; the pre-1.5.0 `showDialog` on IntegrityTokenResponse/StandardIntegrityToken is deprecated in favor of the manager-level method; and 1.5.0 also raised the minimum API level to 23 for both request modes. Also worth saying on stage: this is Google explicitly acknowledging that enforcement without recovery is just churn, which was the entire thesis of tiered enforcement all along.


---

## Part 6: The Policy Engine (where verdicts become decisions)

This is your home turf from Cash App, formalized. It lives server-side, but keep it in the doc for two reasons: tiered enforcement is the thesis of your talk, and the client is where every one of these decisions becomes visible (step-up UI, remediation dialogs, blocked states). Read the code below as a concept sketch for slides, not backend plumbing. The mental model to teach: **attestation classifies, policy decides, product absorbs.** Signals in, action out, and the mapping is data, not code. The client's job in this picture is threefold: carry the token, render the outcome, and never contain the decision.

```kotlin
data class RiskContext(
    val tier: TrustTier,                 // from Part 3
    val licensed: Boolean,
    val activityLevel: Int?,             // recentDeviceActivity, 1..4
    val accessRisk: Set<String>,         // appAccessRiskVerdict labels
    val playProtect: String?,            // playProtectVerdict
    val recallBits: RecallBits?,         // deviceRecall
    val actionSensitivity: Sensitivity   // what is being attempted
)

enum class Sensitivity { LOW, MEDIUM, HIGH, CRITICAL }

sealed interface PolicyDecision {
    data object Allow : PolicyDecision
    data class StepUp(val method: StepUpMethod) : PolicyDecision          // biometric, OTP...
    data class Remediate(val dialog: Int) : PolicyDecision                // Part 5
    data class ShadowFlag(val reason: String) : PolicyDecision            // allow, but mark
    data class Deny(val userMessage: String) : PolicyDecision
}

fun decide(ctx: RiskContext): PolicyDecision = when {
    // Hard floors first
    ctx.recallBits?.confirmedFraud == true ->
        PolicyDecision.Deny("This action isn't available right now.")

    "UNKNOWN_CONTROLLING" in ctx.accessRisk && ctx.actionSensitivity >= Sensitivity.HIGH ->
        PolicyDecision.Remediate(IntegrityDialogTypeCode.CLOSE_ALL_ACCESS_RISK)

    // Farm pattern: real device, absurd token volume. Don't tip them off.
    ctx.activityLevel == 4 ->
        PolicyDecision.ShadowFlag("token volume LEVEL_4")

    // Tier-vs-sensitivity matrix
    ctx.actionSensitivity == Sensitivity.CRITICAL && ctx.tier != TrustTier.STRONG ->
        PolicyDecision.StepUp(StepUpMethod.BIOMETRIC_PLUS_OTP)

    ctx.actionSensitivity >= Sensitivity.HIGH && ctx.tier < TrustTier.DEVICE ->
        PolicyDecision.Remediate(IntegrityDialogTypeCode.GET_INTEGRITY)

    ctx.tier == TrustTier.NONE ->
        PolicyDecision.Deny("Please reinstall the app from Google Play.")

    else -> PolicyDecision.Allow
}
```

Principles to preach with this slide-ready skeleton:

1. **No single signal is a death sentence except confirmed prior fraud.** Everything else adjusts friction.
2. **Deny is the rarest verb.** The ladder is Allow → ShadowFlag → StepUp → Remediate → Deny. Every rung preserves more good users than the one below it.
3. **Shadow-flagging beats blocking for adversaries.** Blocking teaches the attacker your detection boundary in real time; silent flagging feeds your fraud models without leaking signal. (Your 13%-of-login-blocks stat becomes richer here: those were the *visible* enforcement tip of a larger silent iceberg.)
4. **The matrix is config, not code.** When May-2025-style ground shifts happen, you retune thresholds in a config push, not an app release. This was the operational lesson of the migration era.
5. **Observe before enforce, always.** Google now codifies your hard-won practice as official rollout guidance: integrate without enforcement, baseline your real population, estimate blast radius, then ratchet.

---

## Part 7: Testing, Operations, and Failure Planning

### 7.1 Play Console test responses

You can configure synthetic verdict responses per license-tester account in Play Console (Protected with Play → Play Integrity API → Test responses), letting you exercise every branch of the policy engine (UNLICENSED, missing STRONG, HIGH_RISK Play Protect, recall bits set) on a healthy physical device without rooting anything. Remember: toggling device recall wipes these configurations. Google also ships an official demo (sample app + Node backend) covering tiered content delivery, access-risk protection, error handling, and remediation dialogs; worth cloning before the conference so you can speak to it.

### 7.2 Local troubleshooting

For a device mysteriously failing DEVICE integrity in testing: confirm factory ROM and locked bootloader, check the device's Play Protect certification status in Play Store settings, and re-test after a reset. Adb-sideloaded debug builds are expected to return UNRECOGNIZED_VERSION and UNLICENSED; internal testing tracks exist precisely so your team's builds are Play-known.

### 7.3 Outage posture

Google publishes Play Integrity status on the Play status dashboard, and you must decide *in advance* what your backend does during an API outage: fail open (allow with shadow-flagging and tightened downstream limits) or fail closed (block sensitive actions). For a fintech, the defensible answer is usually action-dependent: fail open for reads and low-value actions with elevated logging, fail closed only for the CRITICAL tier, and communicate via feature flags so the posture can flip without deploys. Whatever the choice, rehearse it; the mid-incident version of this decision is always worse.

### 7.4 Quota reality check

Defaults: 10,000 decodeIntegrityToken calls/day (raisable via Play Console request), per-instance warm-up limits (5 prepareIntegrityToken/minute), and separate rate limits for deviceRecall writes that don't consume token quota. Your quota SEV story slots here: the failure mode of quota exhaustion is silent verdict starvation, and the mitigation stack is caching strategy (Standard's built-in caching vs your attestation frequency), quota increase runway, and backend behavior when verdicts stop arriving (see outage posture).

---

## Part 8: Hard Questions You Might Get From the Audience (with answers)

**"Can't attackers just use leaked keyboxes to spoof STRONG?"**
Yes, temporarily, and Google revokes them; the bypass community itself reports breakage every few weeks. The defense goal is economic: raising the cost and shortening the shelf life of each bypass until fraud is unprofitable. Defense in depth (request binding, activity levels, recall, backend models) means a spoofed device verdict alone still doesn't unlock much.

**"What about devices outside Play services: Huawei, AOSP, F-Droid users?"**
Honest answer: Play Integrity is a Play-ecosystem tool; devices without Play services return empty/UNEVALUATED verdicts. Your policy engine must have an explicit lane for "no verdict available" that matches your product's risk tolerance and market. That lane existing at all is a policy decision, not an API feature.

**"Isn't this hostile to rooted power users?"**
The May 2025 changes did hit them hardest, and this is precisely the argument for tiered enforcement: a rooted device holding BASIC can be allowed to browse and do low-risk actions while being stepped-up or excluded from CRITICAL ones. Blanket blocks below DEVICE are a product choice the API doesn't force on you.

**"Why not just use Firebase App Check?"**
App Check wraps Play Integrity for protecting Firebase/Google Cloud resources with configurable token TTLs; it answers "is this my app talking to my Firebase backend." Direct Play Integrity integration gives the full verdict set (access risk, Play Protect, activity, recall) and request binding for your own APIs. Many apps legitimately run both.

**"How is deviceRecall different from device fingerprinting?"**
Fingerprinting derives a covert stable ID from device characteristics; recall stores three explicit, developer-defined, Google-hosted bits that you can also clear, with no cross-developer linkability (bits are scoped to your developer account). It's memory with governance, not identification.

**"What happens with the Feb 2026 root rotation?"**
Nothing, if you use Play Integrity; Google absorbed it. Direct key-attestation implementations had to update trust stores. This asymmetry is much of the API's operational value.

---

## Part 9: Cheat Sheet

- May 2025: all device verdicts hardware-backed on Android 13+; STRONG requires ≤12-month-old security patch; Play install/update required for full verdicts; ~90% fewer signals collected; up to ~80% latency improvement; ecosystem-level dynamic verdict adjustment.
- Feb 2026: platform attestation root rotated; Play Integrity users unaffected.
- Verdict order of operations: requestDetails → appIntegrity (+cert digest) → accountDetails → deviceIntegrity → environmentDetails.
- Tiers are cumulative labels; check membership, never equality. Empty verdict is worse than BASIC.
- requestHash: ≤500 chars, hash of canonical action semantics, recomputed server-side.
- Freshness stack: idempotency binding → single-use registry → timestamp window.
- recentDeviceActivity: LEVEL_1 ≈ ≤10/hr ... LEVEL_4 > 50/hr; baseline before enforcing; shadow-flag farms.
- appAccessRisk: KNOWN/UNKNOWN × INSTALLED/CAPTURING/CONTROLLING; verified accessibility tools excluded; per-request opt-out available.
- playProtectVerdict: NO_ISSUES / NO_DATA / POSSIBLE_RISK / MEDIUM_RISK / HIGH_RISK / UNEVALUATED; device hygiene, not session attack.
- deviceRecall (beta): 3 bits + YYYYMM write dates, survives factory reset, 14-day write window per token, ~30s propagation, 3-year retention, shared across your dev account's apps.
- Remediation dialogs (lib 1.5.0+, minSdk 23): GET_INTEGRITY, GET_STRONG_INTEGRITY, GET_LICENSED, CLOSE_UNKNOWN/ALL_ACCESS_RISK; one showDialog per response; re-warm provider after success.
- Enforcement ladder: Allow → ShadowFlag → StepUp → Remediate → Deny.
- Ecosystem stat for the deck: apps using Play Integrity see ~80% lower unauthorized usage on average (Google, Oct 2025); users include Uber, TikTok, Stripe, Paytm.
