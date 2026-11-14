# Architecture

```
app (Android, Compose)  ──►  engine (pure Kotlin)  ──►  core (pure Kotlin)
tools (JVM CLI)         ──►  engine
```

* **Dependency direction is one-way.** `core` knows nothing about the engine; the engine knows
  nothing about Android. The whole game runs headless on any JVM, which is how the long-term
  stress tests and the CLI work.
* **Local-first.** Everything runs offline. There is no network code in the MVP.

## Engine packages (each a façade with a clear responsibility)

| Package | Responsibility | Main entry points |
|---|---|---|
| `world` | Default world generation, name pools, person factory | `WorldGenerator.generate`, `Factory.player/staff` |
| `ability` | CA from attributes per position, familiarity, team strength | `Ability.caAt/effective`, `Strength.of` |
| `competition` | Data-driven leagues and cups: fixtures, tables, tiebreakers, knockouts, promotion/relegation | `CompetitionEngine` |
| `match` | Lineup selection, 2D agent engine (FULL), reduced & light models, penalties | `MatchEngine2D`, `QuickSim`, `Lineups` |
| `development` | Weekly training/growth/decline, injuries, youth intake, retirement, staff turnover | `Development` |
| `market` | Valuation, AI transfers, contracts, renewals, loans, user negotiations | `Transfers`, `Valuation` |
| `finance` | Ledger (income/expense categories), monthly cycle, budgets, owner rescues | `Finance` |
| `ai` | Boards, expectations, sackings, manager market, job offers, AI match tactics | `Board`, `AiTactics` |
| `game` | Day pipeline, result application, season rollover, scouting, user API | `Career`, `Validator` |
| `save` | Slots, atomic writes, checksums, backups, migrations | `SaveStore`, `Migrations` |

## The day pipeline (`Career.advanceDay`)

Order is fixed and part of determinism:

1. 1st of month: club finances, board review, market values
2. today's fixtures (AI matches with REDUCED or LIGHT model; the user's match must be played first)
3. daily recovery (condition, injuries, morale)
4. Mondays: training & development, training injuries, scouting
5. transfer window activity (AI clubs act on a rotating schedule)
6. calendar events: renewals (15 Jan, 1 Jun), youth intake (15 Mar), season end (30 Jun)

## Determinism

All randomness comes from `Rng.stream(worldSeed, domain, keys…)`. Each system/entity/day gets
its own independent stream, so one change cannot shift randomness elsewhere. No hash-ordered
collections are iterated in simulation code (only insertion-ordered `LinkedHashMap`s and sorted
lists). Tested by `DeterminismAndSaveTest`.

## Match engine (summary)

* Fixed timestep of 0.1 s match time; 22 agents; ball with height, gravity, rolling friction and bounce.
* On the ball: utility over shoot / pass (with lead, lane risk, offside awareness) / carry / clear,
  scored with a possession-value function (xG × open goal × pressure) and decision noise based on attributes.
* Off the ball: formation slots shifted by ball position and team instructions; role offsets
  (wing-backs, inverted full-backs, false nine...); timed runs beyond the line; box crashing;
  support angles; pressing triggers by engagement line and pressing intensity; goal-side marking.
* Rules: offside at pass time, fouls/cards/injuries from tackles, penalties, corners, free kicks,
  throw-ins, goal kicks, substitutions, extra time and penalty shoot-outs.
* Simulation is separate from rendering: the Android `MatchScreen` steps the engine from the frame
  clock and the Canvas only reads positions.
* Calibration knobs live in `MatchEngine2D.Companion`; `MatchCalibrationTest` and `MatchSweepTest` check them.

## Android layer

* `GameViewModel` owns the `Career`; all mutations run on `Dispatchers.Default` under a mutex,
  with a progress overlay, so the UI never reads a half-updated world.
* State-based navigation (`Screen` sealed class), Material 3, dark/light themes, Indonesian/English.
* Saves in `filesDir/saves`; autosave after each continue; save on `onStop`.

## Architecture decision record

| Decision | Choice | Notes |
|---|---|---|
| Save format (MVP) | gzip(CBOR(World)) snapshot per slot with checksum, atomic rename, 3 rotating backups | **Changed from the blueprint's SQLite plan.** The MVP world (~3,000 players) serialises to ~1 MB, loads in well under a second, and keeps exact equality for determinism tests. SQLite (SQLDelight) is kept behind `SaveStore` as the upgrade path when the world grows to tens of thousands of players. |
| Rendering | Compose Canvas | No LibGDX; 22 dots and a ball do not need a game engine. |
| DI | Constructor/object wiring | Few singletons; no annotation processing. |
