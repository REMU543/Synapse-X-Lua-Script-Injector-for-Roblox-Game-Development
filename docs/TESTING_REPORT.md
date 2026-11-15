# Testing Report — 2026-11-14

Environment: cloud Linux container, OpenJDK 21, Gradle 8.14.3, Android SDK 35. No device or
emulator (no KVM), so the Android UI is tested with Robolectric on the JVM.

## Automated suites

| Suite | Command | Result |
|---|---|---|
| Engine unit/integration (34 tests) | `./gradlew :engine:test` | ✅ all pass (~1 min) |
| Android UI tests, landscape phone + tablet (Robolectric + Compose, with screenshots) | `./gradlew :app:testDebugUnitTest` | ✅ pass |
| 30-season stress test | `./gradlew :engine:test --tests '*StressTest*' -Pstress` | ✅ pass (~6 min) |

### What the tests cover (master prompt §21)

| Question | Test | Answer |
|---|---|---|
| Can a player retire? | `MarketAndDevelopmentTest.retirementAndYouthIntake`, stress test (4,764 archived) | ✅ |
| Can new players be generated? | same, stress test (population stays 3.2k–3.8k) | ✅ |
| Can clubs promote and relegate? | `SeasonTest` (3 up / 3 down verified), stress test | ✅ |
| Can competitions complete full seasons? | `SeasonTest` (leagues, national cup, continental cup) | ✅ |
| Can AI clubs execute transfers? | `SeasonTest`, stress test (~200 transfers/season) | ✅ |
| Can a career progress for decades? | stress test, 30 seasons, validator clean every season; user sacked and re-hired along the way | ✅ |
| Can a save be restored without data loss? | `DeterminismAndSaveTest` (hash-identical reload and identical continuation; corrupt file detected; backup recovery) | ✅ |
| Can offline gameplay operate without network access? | the game contains no network code; the app declares no INTERNET permission | ✅ |
| Determinism | same seed ⇒ identical world after 120 days; match engine deterministic | ✅ |
| User transfers & finances | bid accepted, money moves, ledger consistent; low bids rejected/countered | ✅ |
| Development | most youngsters improve, most veterans decline, CA never exceeds PA | ✅ |
| UI flow (landscape) | start → new career → all screens → private chat → match day: press → briefing → team talk → live match → report → full-time talk → press → home → save → load | ✅ |
| Dynamics | hierarchy, unhappy benched star requests transfer, broken promise consequences, personality-dependent chats, team-talk suggestions, full match-day state machine | ✅ |

### Match engine calibration (`MatchCalibrationTest`, 60 full matches; 2,000 for quick models)

| Metric | FULL 2D | REDUCED | LIGHT | Real-world target |
|---|---|---|---|---|
| Goals / match | 2.45 | 2.39 | 2.49 | 2.5–2.8 |
| Home win % | 35–43 | 43 | 43 | ~45 |
| Draw % | 23–28 | 27 | 26 | ~26 |
| Shots / team | 18 | 11 | 11 | 12–14 |
| Pass completion | 77% | 78% | – | 75–85% |
| Penalties / match | ~0.2 | – | – | ~0.25 |
| Red cards / match | 0.15 | ~0.07 | – | ~0.15 |

## Known issues / observations
* FULL engine: corners (~1.2/team) are rarer than real football (~5); shots slightly high.
* Long-term: one club won the Indonesian top flight in ~16 of 30 seasons. Rich-get-richer effects
  (reputation → revenue → squad) need balancing.
* Average CA in the top league drifts from ~85 to ~100 over 15 seasons, then stabilises.
* The stress test plays the user's club on autopilot (no transfers), so its league positions are not
  representative of human play.
* Not tested on physical Android hardware yet.
