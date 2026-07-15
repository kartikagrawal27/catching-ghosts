# The Gotcha Catalog

Every trap we know about, in the order you're likely to meet them. The ones marked 🔥 cost someone (sometimes us) a production incident.

## Setup & builds
1. **Cloud project number, not project ID.** Warm-up wants the long integer from Play Console; the string ID fails with CLOUD_PROJECT_NUMBER_IS_INVALID.
2. **Console linkage first.** No linked cloud project under App integrity → nothing works, with unhelpful errors.
3. **Opt-in fields are invisible until enabled.** environmentDetails, recentDeviceActivity, deviceAttributes, deviceRecall appear in payloads only after console opt-in. Absent ≠ broken.
4. **The deviceRecall toggle wipes configured test responses.** Flip it deliberately.
5. **minSdk 23** as of library 1.5.0, both request modes.
6. **Upload key ≠ app signing key.** With Play App Signing, your locally-signed release APK can fail appIntegrity even though "the code is identical" — Play distributes builds signed with the app signing key.
7. **Your debug builds are Imposters.** adb installs return UNRECOGNIZED_VERSION + UNLICENSED by design. Use internal testing tracks for integrity work.

## Requesting tokens
8. 🔥 **Warm-ups draw from the same quota economy.** Warming the provider per call multiplies consumption until -8 TOO_MANY_REQUESTS floods and verdicts silently starve. One provider, app scope, re-warm on invalidation.
9. **No documented provider TTL.** The contract is reactive: reuse until INTEGRITY_TOKEN_PROVIDER_INVALID (-19), then re-warm. Daily proactive refresh is a heuristic for long-lived processes only.
10. **requestHash ≤ 500 chars** (REQUEST_HASH_TOO_LONG otherwise). Hash your canonical string; never stuff raw payloads in.
11. **Nonce rules (Classic):** 16–500 chars, URL-safe Base64, server-generated, single-use, no PII.
12. **Warm-up rate limits are per-instance (~5/min).** Retry loops around prepareIntegrityToken hit them fast.
13. **Attest at trust boundaries, not everywhere.** Beyond quota: over-attesting inflates your own users' recentDeviceActivity levels, shrinking the farm-detection gap.

## Verdict handling
14. 🔥 **Labels are cumulative — check `contains`, never equality.** A healthy flagship holds all three tiers; `== DEVICE` silently misclassifies your best users. No crash, just a false-positive trend weeks later.
15. **Empty verdict is worse than BASIC.** It means Play couldn't establish anything. Route to your most cautious lane.
16. **The same label means different things by OS era.** Android 13+ uses the post-May-2025 hardware-anchored definitions (STRONG requires ≤12-month patch); 6–12 uses legacy ones. deviceAttributes.sdkVersion (attested, unspoofable) disambiguates.
17. 🔥 **STRONG decays.** A device nobody touched drops out of STRONG when its last security patch ages past 12 months. Never make STRONG a general floor; reserve it for step-up.
18. **Verify the cert digest, not just PLAY_RECOGNIZED.** Belt and suspenders against rollout edge cases.
19. **Don't cache Classic verdicts for frequent checks.** Verdicts are timestamped observations, not durable facts (see The Sleepwalker).
20. **Play-install requirement (Android 13+).** Sideloaded and some enterprise-provisioned installs stopped receiving full verdicts in May 2025; MDM flows needed migration.

## Binding & freshness
21. 🔥 **Canonical drift kills binding.** Two independent canonical-string builders will diverge (field order, encoding, nulls). Symptom A: mystery failures. Symptom B: someone downgrades mismatch to log-and-allow and your binding becomes decorative. Wire protocol: documented, versioned, golden-tested in CI on both sides.
22. **No PII in the canonical string.** Hashes of low-entropy PII are dictionary-reversible.
23. **Freshness stack is three layers:** action binding → single-use registry → timestamp window. Each catches what the previous misses.

## Dialogs & errors
24. **One showDialog per response object.** The response is a snapshot; to show again, mint fresh.
25. 🔥 **Re-warm after DIALOG_SUCCESSFUL.** The user just changed the device's state; the provider's cached evaluation is stale by definition.
26. **The backend picks the dialog.** It saw the verdict; the client renders the code it's told.
27. **Four error buckets, four responses.** Transient → backoff (5s/10s/20s, cap ~3). Remediable → Play dialog. Provider-stale → re-warm once. Terminal → fix your build; retrying is lying to yourself.
28. **Integrations rot in the failure listener.** Everyone copies the request code correctly.

## Client hygiene
29. 🔥 **Never persist attestation state in SharedPreferences** — plaintext XML on disk. Better: don't persist at all (tokens are single-use; the provider lives in memory). If something truly must persist, Keystore-backed encryption.
30. **The client never decides trust.** Any client-side `if (verdict...)` is a decision made on hardware the attacker owns.

## Operations
31. **Decide outage posture in advance,** per action class, behind a flag: fail open + shadow-flag for low-value, fail closed for critical. The mid-incident version of this decision is always worse.
32. **Observe before enforce. Always.** Integrate silent, baseline verdict distribution and quota, estimate blast radius, ratchet via config.
33. **Quota pressure is an architecture smell.** Rethink cadence before requesting raises.
34. **deviceRecall bits are shared account-wide.** Two teams assigning different meanings to bitFirst is an organizational incident waiting. Govern them; decay old judgments — devices get resold.
