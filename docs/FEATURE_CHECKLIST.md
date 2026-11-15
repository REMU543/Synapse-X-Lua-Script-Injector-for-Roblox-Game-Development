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
- ✅ National teams for all 10 nations: 23-man call-ups each international window (4 per season), friendlies, caps & international goals, injuries on duty
- ✅ Biennial Asia-Pacific Cup (even years, December): seeded draw, 2 groups of 4, semi-finals, final with extra time/penalties; history of winners
- ✅ Database packs: JSON (clubs + players) and CSV (players) import with validation (errors block, warnings inform); export of the current world as an editable pack; import on the new-career screen, export in Settings; CLI `export-pack` / `validate-pack`
- 🟡 In-app editor: edit through the exported JSON (any text editor) and re-import; no on-device field editor yet

## Management
- ✅ Squad screen (sortable), player profile (attributes, contract, history), squad statuses
- ✅ Tactics: 6 formations, 13 team instructions grouped by phase (in possession / transition / out of possession) + offside trap, custom lineup, familiarity
- ✅ 36 player roles with position-specific role menus and duties (Defend / Support / Attack) validated per role
- ✅ Role suitability stars per player (key attributes per role), colour ring on the tactics pitch
- ✅ 8 individual player instructions (shoot more, dribble more, cross more, close down more, stay wider, hold position, get forward, tackle harder) — all used by the 2D engine
- ✅ Set pieces: captain, penalty / free-kick / corner takers, corner routine (near post, far post, penalty spot, edge of area, mixed)
- ✅ Weekly training schedule (9 session types per day) with load, condition and injury effects; quick presets
- ✅ Individual training: attribute focus and position retraining (learnt after weeks, depending on adaptability)
- ✅ Training focus & intensity, coaching quality, facilities; tactical sessions raise tactical familiarity
- ✅ Transfers: bids, counters, wage demands, agent fees, free agents, release, transfer list, AI bids for listed players
- 🟡 Loans (AI loans of young players; no user loan UI yet)
- 🟡 Release clauses / sell-on (data model; release clause honoured in AI bids, no UI to set them)
- ✅ Contracts: renewals (AI and user), expiry, youth contracts
- ✅ Finances: ledger by category, monthly cycle, budgets, sustainability rule, prize money, owner rescue
- ✅ Staff: club staff table, staff market by role (always stocked), hire with quotas and reputation check, dismiss with compensation

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

## Reserves
- ✅ First team / reserves-U21 split (AI and user), manual moves from squad and player profile
- ✅ Weekly reserve league for top-flight clubs with table and reserve stats; fringe first-teamers get reserve minutes
- ✅ Reserve minutes count towards development; seniors stuck in the reserves become unhappy

## Data hub
- ✅ Team performance per match vs league average (goals, xG for/against, shots, possession, pass %, corners, fouls)
- ✅ xG trend chart for the last 10 matches
- ✅ Squad statistics table with per-90 values and caps; league leaders (goals, assists, rating, clean sheets)

## Match day (management-sim style)
- ✅ Pre-match press conference (context-aware questions; answers affect morale, media hype, board)
- ✅ Briefing: opposition analysis (form, position, expected formation & mentality, key players, predicted XI), win/draw/loss prediction, assistant advice, our line-up with condition & morale
- ✅ Team talks before kick-off, at half-time and at full-time; per-player reactions by personality & situation; assistant suggestion; motivation affects the 2D engine
- ✅ Live match: side panel with live ratings & condition, commentary / stats / assistant / tactics tabs, subs dialog, speeds, pause
- ✅ Live assistant advice (possession, shots conceded, chance quality, chasing/protecting, fatigue, bookings, poor performers)
- ✅ Post-match report: scorers, cards, player of the match, stat bars, ratings for both teams
- ✅ Post-match press conference

## Squad dynamics & interaction
- ✅ Influence hierarchy (team leaders, highly influential, influential, others)
- ✅ Happiness with explained factors (playing time vs status, contract, wage, relationship, form, ambition, broken/kept promises, injuries)
- ✅ Dressing-room atmosphere, cohesion, social groups, media hype
- ✅ Private conversations (praise/criticise form or training, playing time, future, discipline, transfer request) with personality-dependent outcomes and cooldown
- ✅ Promises (playing time, contract, signing, sale) tracked, kept or broken with dressing-room consequences
- ✅ Unhappy players complain and eventually hand in transfer requests

## Android
- ✅ Whole app in landscape; responsive shell: labelled sidebar on tablets, icon rail on phones; persistent top bar with Continue / Match Day
- ✅ Dense tables (up to 12 columns on wide screens), multi-column dashboards, compact typography on phones
- ✅ Compose UI: start, new career, home, inbox, squad, dynamics, player, tactics, training, fixtures/competitions, scouting, transfers, club, finances, manager, settings, match-day flow
- ✅ Four selectable themes (Charcoal, Midnight, Light, High Contrast), Indonesian/English (remembered)
- ✅ Robolectric UI tests in landscape (phone 891×411dp and tablet 1280×800dp) with screenshots
- 🟡 Tested only on JVM (Robolectric); not yet on a physical device
- ⬜ Release signing, store listing

## Online (optional)
- ⬜ Cloud backup / sync, community databases, leaderboards (deliberately not in MVP; no paid services)
