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

## 2026-11-14 — Landscape UI, match day and dynamics
- User asked for an interface and features as close as possible to FM24/FM26, landscape only.
  Agreed scope: genre conventions (sidebar navigation, dense tables, panels, match-day flow) with an
  original look; not a copy of another game's skin or assets.
- User choices: whole app landscape; priorities "match day" and "dynamics & interaction"; phones and
  tablets; selectable themes.
- Engine: `dynamics` package (Dynamics, Interactions, TeamTalk, Press), `game/MatchDay.kt` (Assistant
  preview/live advice/report, MatchDay state machine), in-match motivation in the 2D engine, schema v2.
- UI: new shell, compact typography for phones, dashboard grids, 12-column squad table, two-pane
  tactics, dynamics screen, chat dialog, full match-day flow with landscape live match.
- Bugs fixed: red cards 0.5 → 0.15 per match; live-match controls overflowing on phones; badge text
  contrast; initialisation order of engine fields.
- Tests: 22 engine + 2 landscape UI tests (phone and tablet) with screenshots in `docs/screenshots`.

## 2026-11-14 — Tactics depth, training schedule, staff market
- User asked to keep going in the "same style as FM24". Explained that the visual look of FM24
  (skin, palette, icons, layout) is not copied; continued with its genre-defining depth instead.
- Tactics: duties, 36 roles with per-position menus, role suitability, 8 player instructions, set-piece
  takers and corner routines — wired into the 2D engine (positioning, runs, pressing, decisions, tackles).
- Training: weekly schedule with session loads, daily condition effects, injury risk, tactical familiarity;
  individual attribute focus and position retraining.
- Staff: staff market pool per role, hire/dismiss from a new Staff screen.
- Save schema v3 (defaults only). 28 engine tests + 2 UI tests green.

## 2026-11-15 — National teams, international tournament, data hub
- `international` package: national teams, call-ups per window, friendlies, biennial tournament
  (groups → semi-finals → final), caps/goals, injuries, inbox notices. Simplification: club fixtures are
  not paused during windows; national duty costs condition instead.
- `DataHub`: team averages vs league, xG trend, leaders, per-90 stats from recorded fixture stats.
- UI: International and Data Hub screens in the sidebar; caps on player profile. Save schema v4.

## 2026-11-15 — Reserves and database packs
- Reserves/U21: first-team/reserve split, weekly reserve league (IDN1 clubs), reserve stats, development
  and happiness effects, UI tabs and moves. Save schema v5.
- Database packs: JSON/CSV import with validation, apply to a new world, export of the current world,
  new-career import, settings export, CLI export/validate. Club city/stadium/colours became editable.
