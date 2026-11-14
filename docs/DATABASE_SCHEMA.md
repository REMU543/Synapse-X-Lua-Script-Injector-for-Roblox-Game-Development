# Database / Save Schema (schema version 1)

The MVP persists the whole `World` object graph per career slot (see ARCHITECTURE.md, decision
record). This document is the schema of that graph. Every entity has a stable 64-bit id from a
monotonic sequence (`World.nextId`); ids are never reused.

## File format
`FMA1` magic · int schemaVersion · long CRC32 of body · int header length · header JSON
(`SlotInfo`: manager, club, date, season, saved time) · body = gzip(CBOR(World)).
Atomic write via temp file + rename; previous saves rotate to `.bak1..3`.
Migrations: `Migrations.steps[version]` upgrades N→N+1; new fields get defaults.

## Entities
| Entity | Key fields |
|---|---|
| World | seed, today (epoch day), season, nations, competitions, clubs, players, staff, compSeasons, manager, inbox, news, transfers, archive, seasonHistory, settings, populationTarget |
| Nation | id, code, names, continent, reputation, youthRating, namePool, wageIndex, ticketPrice, playable |
| Club | id, nationId, names, city, reputation, stadium, facilities, colours, balance, budgets, leagueId, managerId, playerIds, staffIds, tactic, boardConfidence, expectedPosition, finance, fanBase, training, honours, form, commercialBase |
| ClubFinance | openingBalance, income{category→amount}, expense{category→amount}, monthly[(day, balance)], seasons[(season, income, expense, endBalance)] |
| Player | id, names, nationId, birthDay, position, secondary, foot, height, weight, attrs (46 × short), pa, personality, injuryProneness, clubId, contract, loanClubId/until, ca, morale, condition, injury, suspension, value, reputation, transferListed, season stats, history (≤25 seasons) |
| Contract | clubId, startDay, endYear (ends 30 June), wage (monthly), releaseClause, sellOnPercent, squadStatus, youth |
| Staff | id, names, nationId, birthDay, role, coaching/tactical/judging/motivation/discipline/medical/youthWork, reputation, style, clubId, contract, formerPlayer, careerLog |
| Competition | id, names, type (LEAGUE/CUP/CONTINENTAL), nationId, tier, reputation, LeagueRules or CupRules (data-driven) |
| CompSeason | compId, season, teams, fixtures, table, currentRound, finished, winner, alive |
| Fixture | id, compId, round, day, home, away, leg/firstLegId, result, ET, penalties, winner, detail level, attendance, compact events, stats |
| ManagerProfile | names, nation, birthDay, licence, style, reputation, clubId, history, achievements, jobOffers, record, scouting knowledge, scoutingNation, shortlist |
| ArchivedPerson | compact record of notable retired players (peak CA ≥ 100, ≥150 apps or ex-user players) |

## Retention (keeps decades-long saves small)
Player history ≤ 25 seasons; transfers ≤ 1,500; archive ≤ 8,000; inbox/news ≤ 300 each; monthly
finance ≤ 24; finance seasons ≤ 60; detailed match events only for the user's matches.
Non-notable retired players are removed entirely.

## Import of real-world data
Not part of the MVP. Planned as CSV/JSON packs supplied by the user, validated by `Validator`
before use (see ROADMAP.md). No real-name data is shipped.
