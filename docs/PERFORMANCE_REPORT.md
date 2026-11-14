# Performance Report — 2026-11-14

Measured on the cloud container (4 vCPU, JVM 21). Mid-range Android devices will be slower;
re-measure on hardware (see roadmap).

| Operation | Time |
|---|---|
| Generate default world (118 clubs, ~3,100 players) | < 1 s |
| Full 2D match, instant (54,000 ticks) | ~290 ms |
| Whole season incl. ~45 user matches in FULL detail and ~2,300 AI matches | ~12 s (≈ 0.3 s per user match + world) |
| Average simulated day (no user match) | ~20 ms |
| Save (gzip CBOR) | ~0.7 MB at start → 1.3 MB after 30 seasons |

* Live match on Android: the engine steps from the frame clock; at 8× speed that is ~8 ticks per
  frame, ≈ 0.05 ms of simulation per frame — rendering dominates.
* Memory: the whole world is held in memory; ~3,600 players plus history stays well under 100 MB of heap.
* Save size grows slowly and plateaus thanks to the retention rules (DATABASE_SCHEMA.md).
* Detail levels: user matches FULL, playable nations REDUCED, others LIGHT; configurable per nation in Settings.

## Raw 30-season stress run
season | players | avg CA top league | archived | ms | save KB
--- | --- | --- | --- | --- | ---
2026 | 3233 | 84.9 | 38 | 13085 | 676
2030 | 3352 | 87.8 | 395 | 11914 | 861
2035 | 3553 | 96.2 | 1077 | 11259 | 1035
2040 | 3771 | 99.7 | 1834 | 12594 | 1177
2045 | 3618 | 99.7 | 2749 | 12403 | 1215
2050 | 3539 | 96.3 | 3848 | 12301 | 1238
2055 | 3666 | 98.1 | 4764 | 12306 | 1307
