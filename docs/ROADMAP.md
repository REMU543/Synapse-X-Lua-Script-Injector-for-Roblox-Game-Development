# Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 Foundation | Gradle modules, core model, RNG, calendar, save system, game loop | ✅ done |
| 2 Football world | World generator, Indonesian pyramid + 4 leagues, cup, continental cup, competition engine | ✅ done |
| 3 Management | Squad, tactics, training, transfers, contracts, loans, finances | ✅ MVP done |
| 4 Match engine | 2D engine, reduced/light models, calibration, live match UI | ✅ MVP done |
| 5 AI systems | Boards, sackings, manager market, AI transfers, AI match tactics | ✅ MVP done |
| 6 Procedural world | Youth intake, retirements, staff turnover, population control | ✅ done, 30-season test |
| 7 Advanced | Media/press, player conversations, national teams, editor & CSV/JSON import, online backup | ⏳ next |
| 8 QA | Device testing, profiling on real hardware, 50-season runs, UI tests | ⏳ partly (JVM) |
| 9 Release | Signed release build, store assets, final docs | ⏳ later (needs your signing key) |

## Next priorities
1. Play-testing on a real device: UI polish, readability of tables, match renderer feel.
2. Match engine: more set-piece variety (corners are too rare), goalkeeper 1-v-1 behaviour.
3. National teams and international tournaments.
4. Media & player interactions (morale conversations, press conferences).
5. CSV/JSON database import with validation; SQLite storage for large imported worlds.
6. Optional cloud backup via the Android Storage Access Framework (no paid services).
