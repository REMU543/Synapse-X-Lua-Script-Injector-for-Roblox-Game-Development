# Feature Checklist

Legend: ✅ implemented and tested · 🟡 implemented, basic / partly tested · ⬜ not yet

## Foundation
- ✅ Multi-module Gradle project (core / engine / tools / app), JDK 17 bytecode
- ✅ Deterministic RNG streams, calendar, exact money (Rupiah), i18n Indonesian/English
- ✅ Save slots: autosave, manual save, CRC checksum, atomic write, 3 rotating backups, recovery, migrations hook
- ✅ World validator (ids, squads, contracts, ledger, competitions)

## Football world
- ✅ Fictional default world: 10 nations, 118 clubs, ~3,100 players, ~1,100 staff
- ✅ Indonesia: 3 tiers (18/18/20) with promotion/relegation + national cup (56 clubs, byes)
- ✅ Malaysia, Thailand, Vietnam, Japan leagues; continental cup (two-legged ties, single-leg final)
- ✅ Data-driven competition rules (points, tiebreakers incl. head-to-head, promotion/relegation links, cup dates)
- ⬜ National teams & international tournaments
- ⬜ CSV/JSON database import & editor

## Management
- ✅ Squad screen (sortable), player profile (attributes, contract, history), squad statuses
- ✅ Tactics: 6 formations, roles, mentality, 13 team instructions + offside trap, custom lineup, familiarity
- ✅ Training focus & intensity, coaching quality, facilities
- ✅ Transfers: bids, counters, wage demands, agent fees, free agents, release, transfer list, AI bids for listed players
- 🟡 Loans (AI loans of young players; no user loan UI yet)
- 🟡 Release clauses / sell-on (data model; release clause honoured in AI bids, no UI to set them)
- ✅ Contracts: renewals (AI and user), expiry, youth contracts
- ✅ Finances: ledger by category, monthly cycle, budgets, sustainability rule, prize money, owner rescue
- 🟡 Staff: generated with attributes/contracts and affect training/scouting/medical; no hire/fire UI yet

## Match engine
- ✅ 2D real-time engine: 22 agents, ball physics with height, passing/shooting/dribbling/tackling/interceptions, crosses & headers, goalkeeping, fouls, cards, injuries, offside, corners, throw-ins, free kicks, penalties, extra time, shoot-outs
- ✅ Attributes, roles, instructions, fatigue, morale and familiarity influence play
- ✅ Live viewer: speeds, pause, commentary, stats, substitutions, in-match tactics
- ✅ Reduced and light simulation levels, calibrated to the same targets
- 🟡 Set-piece variety (corners rarer than in real football)

## AI
- ✅ AI managers with style (formation, attacking, directness, pressing, youth trust, risk)
- ✅ Opponent-aware match mentality
- ✅ AI transfer strategy by needs, budget, wage room, age and style; contract renewals; squad trimming
- ✅ Boards: expectations, confidence, sackings, manager market, job offers for the user
- ⬜ Media / press conferences, player conversations, agents as separate actors

## Procedural world
- ✅ Youth intake with nation/club/facility/coach influence, rare wonderkids
- ✅ Development & decline; potential is a ceiling
- ✅ Retirement with archive of notable players; retired players can become staff (same identity)
- ✅ Staff ageing & replacement; unemployed manager pool
- ✅ Population control; 30-season stress test passes

## Android
- ✅ Compose UI: start, new career, home, squad, player, tactics, training, transfers, scouting, competitions, club, finances, inbox, profile, settings, live match
- ✅ Dark/light theme, Indonesian/English, portrait
- ✅ Robolectric UI smoke test of the full flow
- 🟡 Tested only on JVM (Robolectric); not yet on a physical device
- ⬜ Release signing, store listing

## Online (optional)
- ⬜ Cloud backup / sync, community databases, leaderboards (deliberately not in MVP; no paid services)
