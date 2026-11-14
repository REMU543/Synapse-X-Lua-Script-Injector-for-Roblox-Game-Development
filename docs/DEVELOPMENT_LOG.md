# Development Log

## 2026-11-14 — Project discovery
- Inspected repository: no game code; README/`link .txt` unrelated to this project (suspicious download link, not opened).
- Environment: JDK 21, Gradle 8.14.3, Maven Central + Gradle Plugin Portal reachable; Google Maven blocked; no Android SDK.
- Wrote `docs/TECHNICAL_BLUEPRINT.md`. Awaiting approval before implementation.

## 2026-11-14 — Blueprint approved ("berikan yang terbaik dan dapat dijalankan")
- Cleanup: removed `link .txt` (suspicious download link), replaced README.
- Environment: created a personal cloud environment allowing `dl.google.com`, `maven.google.com`
  and later `gcp.mirrors.maven.org` (Maven Central redirects large jars there); installed Android SDK
  35 after the user approved accepting the SDK licence.
- Built core + engine: world generator, competition engine, 2D match engine with calibration
  harness and parameter sweeps, quick sims, market, finances, development, AI boards, saves.
- Decision: MVP saves use a checksummed gzip(CBOR) snapshot instead of SQLite (see ARCHITECTURE.md).
- Bugs found & fixed via tests: random sort selector broke comparator contract; squads could fall
  below 11 after expiries (added pre-season minimum squads); population drift (stronger control);
  2D engine: dribbling into the net, keeper own goals, 1.1 penalties/match from a frame bug,
  over-concentrated scorers; Compose strong skipping prevented screens from refreshing.
- Android app: Compose UI for all main sections and the live 2D match; Robolectric smoke test.
- Results: all tests green; 30-season stress test green.
