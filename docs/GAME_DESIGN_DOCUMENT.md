# Game Design Document

## Vision
A persistent, evolving football universe on Android in which the player builds a managerial career
over decades. Depth, consistency and long-term playability come before feature count.

## Player fantasy & core loop
1. Prepare (squad, tactics, training, transfers) →
2. Play the match (watch the 2D engine or take an instant result) →
3. React (inbox, injuries, board, media) →
4. Season end (promotion/relegation, contracts, youth, retirements) → next season.

## World
* Nations: Indonesia (playable: Liga 1, Liga 2, Liga 3 and the Piala Indonesia cup),
  Malaysia, Thailand, Vietnam, Japan (simulated leagues), Brazil, Korea, Australia, Spain, Nigeria
  (talent sources for foreign signings).
* Continental: Asian Champions Cup (16 clubs, two-legged ties, single-leg final).
* All clubs/players/staff are fictional; names are random combinations of common names.

## Players
* 46 attributes on a 1–20 scale (14 technical, 14 mental, 8 physical, 10 goalkeeping) stored ×100
  so development is smooth.
* CA (1–200) is derived from position-weighted attributes; PA is a hard ceiling, not a guarantee.
* Hidden personality (professionalism, ambition, pressure, temperament, loyalty, adaptability,
  consistency, big matches) and injury proneness.
* Development each week: headroom × age curve × training (facilities + coaches) × playing time ×
  professionalism × training intensity, with decline after ~29 (physical first).

## Manager & club
* Manager profile with nationality, licence, playing past, preferred formation and style,
  reputation, history, achievements, W/D/L record and sackings.
* Clubs: reputation, stadium, facilities, youth recruitment, fan base, finances, board confidence
  and expectations, staff (assistant, coaches, GK coach, fitness coach, scout, physio, youth coach).
* Board: sets the expected finish from reputation; confidence moves with results; AI boards sack
  managers; the user can be sacked and then receives job offers.

## Tactics
Six formations (4-3-3, 4-2-3-1, 4-4-2, 3-5-2, 3-4-3, 4-1-4-1) with roles per slot, mentality and
13 numeric team instructions plus offside trap. Every instruction changes a concrete term in the
match engine (see ARCHITECTURE.md). Familiarity drops when changing formation and grows weekly.

## Market
Windows Jul–Aug and January. AI clubs judge needs per line, budget, wage room, age and the
manager's youth trust / risk tolerance; sellers accept based on value, squad depth, key-player
status and release clauses; players accept based on reputation, wage, ambition and adaptability.
Free agents, loans of young players, contract renewals and expiries, agent fees.

## Economy
All money is exact Rupiah. Income: TV, matchday (attendance model), sponsorship/commercial
(reputation-scaled), prize money, sales. Expenses: wages, staff, upkeep, purchases, agent fees.
Wages and values are anchored to a per-nation wage index, so there is no long-term inflation.
Budgets follow a sustainability rule (wages ≤ ~85% of revenue). Owners rescue insolvent clubs.

## Scouting
Knowledge per player (0–100). Own league ≈55%, own nation ≈30%, elsewhere ≈10%; assigned scouts
raise knowledge weekly according to their judging attributes. Attributes are shown as ranges and
potential as noisy star ratings until knowledge is high.

## Simulation detail levels
FULL (2D agents) for the user's matches; REDUCED (minute model with scorers, cards, injuries)
for nations marked as detailed; LIGHT (Poisson) for the rest. All calibrated to the same targets.

## Match day flow
Press conference → briefing (opposition analysis, prediction, assistant advice) → pre-match team
talk → live match (half-time team talk) → match report → full-time team talk → press conference.
Every step can be skipped; the assistant can attend the press conferences.

Team talk tones: calm, passionate, assertive, praise, criticise, no pressure, demand more. Each
tone has a base effect for the situation (stage, score, favourite/underdog), modified per player by
temperament, professionalism, pressure handling, ambition, morale and relationship with the manager.
Reactions change morale and give an in-match motivation of up to ±5% on effective attributes.

## Squad dynamics
Influence = leadership, reputation, age, tenure, status and determination. Happiness = base +
explained factors (see FEATURE_CHECKLIST). Atmosphere = influence-weighted happiness. Unhappiness
lowers morale; long unhappiness produces complaints and transfer requests. Promises have a 70-day
deadline; broken promises hit the player hard and spread to the leaders of the dressing room.

## Interface principles
Landscape only. Information-dense panels and sortable tables in the tradition of desktop football
management games, but with an original visual identity (own palettes, layout and icons; no assets,
skins or trademarks from other games).

## Roles, duties and player instructions
Each formation slot has a role from a position-specific menu and a duty allowed by that role.
In the 2D engine: Attack duty pushes the player 5 m higher and makes him run beyond the line more
eagerly; Defend duty keeps him 6 m deeper and stops forward runs. Roles add their own movement
(wing-backs and complete wing-backs push high, half-backs drop between the centre-backs, mezzalas
drift wide and high, shadow strikers arrive late, inverted wingers come inside, pressing forwards and
ball-winners join the press, sweeper keepers start higher). Player instructions change single terms:
shooting threshold & bias, dribble and cross utility, pressing eagerness, width, forward runs,
tackle rate/strength/foul risk.

## Training
Seven daily sessions with a load each. Development of an attribute is multiplied by the sessions that
train it; the weekly load scales intensity and training-injury risk; rest/recovery days restore
condition, fitness days drain it. Individual focus doubles growth of one attribute. Retraining a
position takes 8–26 weeks depending on adaptability and how related the position is.
