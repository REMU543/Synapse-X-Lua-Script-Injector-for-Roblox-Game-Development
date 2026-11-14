# Manajer Bola — Football Manager Android (working title)

An offline-first football management simulation for Android, written in Kotlin. You manage a club
across decades in a fictional, procedurally evolving football world built on real-style
league structures (Indonesia playable, with Malaysia, Thailand, Vietnam and Japan simulated
in the background, plus a continental cup).

> All clubs, players and staff are fictional and generated deterministically from a world seed.
> No proprietary data, code, names or assets from other games are used.

## Status

The engine is playable end to end (career → seasons → decades) and covered by tests,
including a 30-season stress test. The Android app (Jetpack Compose) builds into a debug APK.
See [FEATURE_CHECKLIST.md](docs/FEATURE_CHECKLIST.md) for what is done and what is not.

## Modules

| Module | What it is |
|---|---|
| `core` | Pure Kotlin domain model, deterministic RNG, calendar, money, attributes, tactics, i18n (Indonesian/English) |
| `engine` | Simulation: world generation, competitions, 2D match engine, market, finance, development, AI, save system |
| `tools` | JVM command-line tools: headless multi-season simulation, a text-mode career, save validation |
| `app` | Android app (Jetpack Compose UI, Canvas match renderer). Built only when an Android SDK is present |

## Build & run

Requirements: JDK 17+ (tested with 21). The Android app also needs the Android SDK (platform 35, build-tools 35).

```bash
./gradlew :engine:test                       # unit + integration tests (~1 min)
./gradlew :engine:test --tests '*StressTest*' -Pstress            # 30-season stress test
./gradlew :engine:test --tests '*StressTest*' -Pstress -Dseasons=50
./gradlew :tools:run --args="simulate --seasons 10 --seed 2026"   # headless simulation report
./gradlew :tools:run --args="play" -q --console=plain             # play a career in the terminal
./gradlew :app:assembleDebug                 # APK at app/build/outputs/apk/debug/app-debug.apk
```

Install on a device: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (or copy the APK
to the phone and open it with "install unknown apps" allowed).

## Documentation

- [Technical blueprint](docs/TECHNICAL_BLUEPRINT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Game design document](docs/GAME_DESIGN_DOCUMENT.md)
- [Database / save schema](docs/DATABASE_SCHEMA.md)
- [Roadmap](docs/ROADMAP.md) · [Feature checklist](docs/FEATURE_CHECKLIST.md)
- [Development log](docs/DEVELOPMENT_LOG.md) · [Testing report](docs/TESTING_REPORT.md) · [Performance report](docs/PERFORMANCE_REPORT.md)

## License

Apache-2.0 (see `LICENSE`).
