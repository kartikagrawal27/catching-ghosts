# The Full Bestiary 👻

Six ghosts. Four made the talk; two are printed here because forty minutes is forty minutes. Each entry: the attack, what it exploits, what catches it, and the mistake defenders make.

---

## 1 · The Hollow — *the device that doesn't exist*
**The attack:** emulator farms and spoofed-hardware rigs, thousands of instances per rack, farming signups, bonuses, and credentials at machine speed for fractions of a cent each.
**Exploits:** the fact that HTTP is testimony, not evidence — traffic from an emulator is byte-identical to a phone's.
**Caught by:** hardware key attestation (post-May-2025, the backbone of all device tiers). An emulator imitates everything above the chip; it cannot produce a signature from silicon it doesn't have. Empty verdicts route to the most cautious lane — empty is worse than BASIC.
**Defender's mistake:** binary enforcement. A rooted hobbyist and a fraud rig can hold the same weak verdict; the verdict reports state, never intent. Use the ladder (Allow → ShadowFlag → StepUp → Remediate → Deny), keep thresholds in config, observe before enforcing — May 2025 punished everyone who didn't.

## 2 · The Pickpocket — *a real token, the wrong request*
**The attack:** token theft, replay, and repurposing. A genuine token minted for a $5 transfer, stapled onto a $5,000 one.
**Exploits:** unbound tokens are bearer instruments — steal once, spend anywhere.
**Caught by:** request binding (requestHash over the canonical action semantics, recomputed server-side), freshness windows (minutes), and single-use registries. One token, one action, one time, soon.
**Defender's mistake:** canonical drift. Client and server build the binding string independently; the day they diverge, either legit requests fail mysteriously or someone "fixes" it by downgrading mismatches from reject to log-and-allow — and the binding becomes decorative. Treat the format as a wire protocol: documented, versioned, golden-tested on both sides.

## 3 · The Farmhand — *real devices, industrial intent*
**The attack:** token farms. Racks of genuine, certified, untouched phones minting valid tokens on demand for bots. Every per-device check passes because nothing is fake.
**Exploits:** attestation answering exactly the question it was asked — "is this device real?" — while the fraud lives one level up.
**Caught by:** rhythm and memory. `recentDeviceActivity` buckets token-minting volume per device per hour (LEVEL_4 = nobody's thumb). `deviceRecall` (beta) persists your judgment across reinstall AND factory reset. And the counterintuitive move: shadow-flag, don't block — blocking teaches the farm your boundary; silent flagging burns their capital.
**Defender's mistake #1:** over-attesting. Levels count *your* app's requests, so attesting on every screen pushes your own humans toward the farm zone.
**Defender's mistake #2:** believing per-device signals end the story. A distributed farm stays at LEVEL_1; that's the asymptotic ghost, defeated economically (hardware scales linearly, binding forces live per-action minting, recall burns devices) and caught at the aggregate layer (account/IP/behavior graphs).

## 4 · The Puppeteer — *a real user, someone else's hands*
**The attack:** remote-control and screen-share scams. A "support agent" talks a real, authenticated customer into installing a capture/control app, then drives the session.
**Exploits:** every classic control checking the wrong thing — device genuine, user authenticated, session legitimate.
**Caught by:** `appAccessRiskVerdict` (is anything capturing or controlling this screen *right now*), Play remediation dialogs (CLOSE_ALL_ACCESS_RISK — Play prompts the user to shut the risky apps; you write none of the UX), and step-ups on the user axis when the device axis can't be fixed. Verified accessibility tools are excluded, so screen-reader users are safe.
**Defender's honesty:** if the scammer only *talks* the victim through it — no software in the session — this signal is silent. That variant is fought with behavioral models, new-payee cooling-off, and scam interstitials. Attestation grew toward "is this session safe"; it hasn't swallowed social engineering whole.

---

## 5 · The Imposter — *your app, re-signed* (cut from the talk)
**The attack:** a tampered or repackaged APK — your app with ad SDKs injected, payment endpoints redirected, or premium checks bypassed, redistributed on third-party stores.
**Exploits:** users who sideload, and any backend that never checks *which binary* is talking to it.
**Caught by:** `appIntegrity` — `PLAY_RECOGNIZED` plus verifying `certificateSha256Digest` against your release cert (check the digest, not just the label). `UNRECOGNIZED_VERSION` = not a build Play knows.
**Defender's mistake:** forgetting their own builds are Imposters — adb-installed debug builds are by definition unrecognized sideloads. Internal testing tracks exist so your team's builds are Play-known.
**Why he was cut:** his defense is one field and one comparison — a fine gotcha, a boring act.

## 6 · The Sleepwalker — *the verdict from another time* (cut from the talk)
**The attack:** stale truth. A token minted before the device was rooted, or a Classic verdict cached "for efficiency" and consulted hours later — the device has changed; the testimony hasn't.
**Exploits:** any architecture that treats a verdict as a durable fact instead of a timestamped observation.
**Caught by:** tight `timestampMillis` windows, never caching Classic verdicts for frequent checks, and re-warming the provider after any remediation dialog (the world just changed; the snapshot didn't).
**Defender's mistake:** the word "cache." Verdicts describe *then*. Trust decisions happen *now*.
