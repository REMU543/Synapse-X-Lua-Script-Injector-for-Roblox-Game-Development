package fm.engine.dynamics

import fm.core.*

/**
 * Private conversations between the manager and a player. The outcome depends on the topic,
 * the player's situation (form, playing time, contract, happiness) and personality.
 */
object Interactions {
    enum class Topic(val id: String, val en: String) {
        PRAISE_FORM("Puji performanya", "Praise his form"),
        CRITICISE_FORM("Kritik performanya", "Criticise his form"),
        PRAISE_TRAINING("Puji latihannya", "Praise his training"),
        CRITICISE_TRAINING("Kritik latihannya", "Criticise his training"),
        PLAYING_TIME("Bahas waktu bermain", "Discuss playing time"),
        FUTURE("Tanya soal masa depannya", "Ask about his future"),
        DISCIPLINE("Tegur soal disiplin", "Warn about discipline"),
        TRANSFER_REQUEST("Bahas permintaan transfer", "Discuss transfer request");
        fun label(lang: Lang) = if (lang == Lang.ID) id else en
    }

    enum class Reply(val id: String, val en: String) {
        PROMISE_TIME("Janjikan lebih banyak waktu bermain", "Promise more playing time"),
        EARN_IT("Ia harus membuktikan diri di latihan", "He must earn it in training"),
        PROMISE_CONTRACT("Janjikan kontrak baru", "Promise a new contract"),
        PROMISE_SALE("Janjikan dijual jika ada tawaran layak", "Promise to sell if a fair offer comes"),
        PROMISE_SIGNING("Janjikan rekrutan baru di posisinya", "Promise to sign a player for his position"),
        REFUSE("Tolak — ia tetap di sini", "Refuse — he stays");
        fun label(lang: Lang) = if (lang == Lang.ID) id else en
    }

    data class Outcome(
        val responseKey: String,
        val args: List<String>,
        val moraleDelta: Int,
        val relationshipDelta: Int,
        /** Replies the manager can give next (empty = conversation over). */
        val followUps: List<Reply> = emptyList(),
        val positive: Boolean,
    )

    fun available(world: World, p: Player): List<Topic> {
        val t = mutableListOf(Topic.PRAISE_FORM, Topic.CRITICISE_FORM, Topic.PRAISE_TRAINING, Topic.CRITICISE_TRAINING, Topic.PLAYING_TIME, Topic.FUTURE)
        if (p.season.yellow >= 4 || p.season.red > 0) t += Topic.DISCIPLINE
        if (p.wantsOut) t += Topic.TRANSFER_REQUEST
        return t
    }

    fun canTalk(world: World, p: Player) = world.today - p.lastChatDay >= 7

    private fun rng(world: World, p: Player) = Rng.stream(world.seed, "chat", p.id, world.today.toLong())

    fun talk(world: World, p: Player, topic: Topic): Outcome {
        val r = rng(world, p)
        val pers = p.personality
        val rating = if (p.season.apps >= 3) p.season.avgRating else 6.7
        val share = Dynamics.playingShare(world, p)
        val exp = Dynamics.expectedShare(p.contract?.squadStatus)
        val o = when (topic) {
            Topic.PRAISE_FORM -> if (rating >= 6.9) Outcome("chat_praise_ok", listOf(p.shortName), 6, 5, positive = true)
            else Outcome("chat_praise_hollow", listOf(p.shortName), -1, -3, positive = false)
            Topic.CRITICISE_FORM -> when {
                rating >= 7.0 -> Outcome("chat_crit_unfair", listOf(p.shortName), -8, -10, positive = false)
                pers.professionalism >= 14 || pers.temperament >= 14 -> Outcome("chat_crit_accept", listOf(p.shortName), 4, 2, positive = true)
                pers.temperament <= 7 -> Outcome("chat_crit_angry", listOf(p.shortName), -10, -12, positive = false)
                else -> Outcome("chat_crit_neutral", listOf(p.shortName), -2, -2, positive = false)
            }
            Topic.PRAISE_TRAINING -> if (pers.professionalism >= 10 || r.chance(0.6)) Outcome("chat_train_praise_ok", listOf(p.shortName), 4, 4, positive = true)
            else Outcome("chat_train_praise_meh", listOf(p.shortName), 0, 0, positive = false)
            Topic.CRITICISE_TRAINING -> if (pers.professionalism <= 9 && pers.temperament >= 9) Outcome("chat_train_crit_ok", listOf(p.shortName), 2, 0, positive = true)
            else Outcome("chat_train_crit_bad", listOf(p.shortName), -6, -8, positive = false)
            Topic.PLAYING_TIME -> if (share >= 0 && share < exp - 0.1) Outcome("chat_time_wants", listOf(p.shortName), 0, 0,
                listOf(Reply.PROMISE_TIME, Reply.EARN_IT), positive = false)
            else Outcome("chat_time_happy", listOf(p.shortName), 2, 2, positive = true)
            Topic.FUTURE -> {
                val worst = Dynamics.factors(world, p).filter { it.value < 0 }.minByOrNull { it.value }
                if (worst == null) Outcome("chat_future_happy", listOf(p.shortName), 2, 3, positive = true)
                else Outcome("chat_future_concern", listOf(p.shortName, worst.label(world.settings.lang).lowercase()), 0, 1,
                    when {
                        worst.keyEn.contains("playing time") -> listOf(Reply.PROMISE_TIME, Reply.EARN_IT)
                        worst.keyEn.contains("Contract") || worst.keyEn.contains("underpaid") -> listOf(Reply.PROMISE_CONTRACT, Reply.REFUSE)
                        else -> emptyList()
                    }, positive = false)
            }
            Topic.DISCIPLINE -> if (pers.temperament >= 11) Outcome("chat_disc_ok", listOf(p.shortName), 0, 1, positive = true)
            else Outcome("chat_disc_bad", listOf(p.shortName), -5, -6, positive = false)
            Topic.TRANSFER_REQUEST -> Outcome("chat_transfer", listOf(p.shortName), 0, 0,
                listOf(Reply.PROMISE_SALE, Reply.PROMISE_CONTRACT, Reply.PROMISE_SIGNING, Reply.REFUSE), positive = false)
        }
        apply(world, p, o)
        return o
    }

    fun reply(world: World, p: Player, reply: Reply): Outcome {
        val days = 70
        fun promise(type: PromiseType): Outcome {
            world.promises.add(Promise(p.id, type, world.today, world.today + days, appsAtStart = p.season.apps))
            if (type == PromiseType.SELL_IF_OFFER) p.transferListed = true
            p.wantsOut = false; p.unhappyWeeks = 0
            return Outcome("chat_promise_made", listOf(p.shortName), 6, 6, positive = true)
        }
        val o = when (reply) {
            Reply.PROMISE_TIME -> promise(PromiseType.PLAYING_TIME)
            Reply.PROMISE_CONTRACT -> promise(PromiseType.NEW_CONTRACT)
            Reply.PROMISE_SALE -> promise(PromiseType.SELL_IF_OFFER)
            Reply.PROMISE_SIGNING -> promise(PromiseType.SIGN_PLAYER)
            Reply.EARN_IT -> if (p.personality.professionalism >= 13) Outcome("chat_earn_ok", listOf(p.shortName), 2, 1, positive = true)
            else Outcome("chat_earn_bad", listOf(p.shortName), -6, -8, positive = false)
            Reply.REFUSE -> Outcome("chat_refuse", listOf(p.shortName), -10, -12, positive = false)
        }
        apply(world, p, o)
        return o
    }

    private fun apply(world: World, p: Player, o: Outcome) {
        p.morale = (p.morale + o.moraleDelta).coerceIn(5, 100)
        p.relationship = (p.relationship + o.relationshipDelta).coerceIn(0, 100)
        p.lastChatDay = world.today
        p.happiness = Dynamics.happiness(world, p)
    }
}

/** Team talks before kick-off, at half-time and at full-time. */
object TeamTalk {
    enum class Stage { PRE, HALF, FULL }
    enum class Tone(val id: String, val en: String) {
        CALM("Tenang", "Calm"),
        PASSIONATE("Penuh semangat", "Passionate"),
        ASSERTIVE("Tegas", "Assertive"),
        PRAISE("Memuji", "Praise"),
        CRITICISE("Mengkritik", "Criticise"),
        NO_PRESSURE("Tanpa tekanan", "No pressure"),
        DEMAND_MORE("Menuntut lebih", "Demand more");
        fun label(lang: Lang) = if (lang == Lang.ID) id else en
    }

    data class Reaction(val playerId: Long, val value: Double) {
        fun label(lang: Lang): String = when {
            value > 0.5 -> if (lang == Lang.ID) "Sangat termotivasi" else "Fired up"
            value > 0.15 -> if (lang == Lang.ID) "Termotivasi" else "Motivated"
            value > -0.15 -> if (lang == Lang.ID) "Tidak terpengaruh" else "Unmoved"
            value > -0.5 -> if (lang == Lang.ID) "Gugup / ragu" else "Nervous"
            else -> if (lang == Lang.ID) "Kesal" else "Annoyed"
        }
    }

    /** Base effectiveness of a tone given the situation (scoreDiff from the user's view; favourite = expected to win). */
    fun base(tone: Tone, stage: Stage, scoreDiff: Int, favourite: Boolean): Double = when (stage) {
        Stage.PRE -> when (tone) {
            Tone.CALM -> 0.3
            Tone.PASSIONATE -> if (favourite) 0.2 else 0.45
            Tone.ASSERTIVE -> if (favourite) 0.35 else 0.0
            Tone.DEMAND_MORE -> if (favourite) 0.45 else -0.35
            Tone.NO_PRESSURE -> if (favourite) -0.3 else 0.5
            Tone.PRAISE -> 0.05
            Tone.CRITICISE -> -0.5
        }
        Stage.HALF -> when {
            scoreDiff > 0 -> when (tone) { Tone.PRAISE -> 0.5; Tone.CALM -> 0.35; Tone.DEMAND_MORE -> if (favourite) 0.2 else -0.2
                Tone.CRITICISE -> -0.6; Tone.ASSERTIVE -> 0.0; Tone.PASSIONATE -> 0.15; Tone.NO_PRESSURE -> 0.1 }
            scoreDiff == 0 -> when (tone) { Tone.PASSIONATE -> 0.4; Tone.DEMAND_MORE -> if (favourite) 0.45 else 0.0
                Tone.ASSERTIVE -> if (favourite) 0.3 else 0.1; Tone.CALM -> 0.2; Tone.PRAISE -> if (favourite) -0.2 else 0.3
                Tone.NO_PRESSURE -> if (favourite) -0.2 else 0.35; Tone.CRITICISE -> if (favourite) 0.1 else -0.4 }
            else -> when (tone) { Tone.ASSERTIVE -> if (favourite) 0.45 else 0.2; Tone.CRITICISE -> if (favourite) 0.3 else -0.4
                Tone.PASSIONATE -> 0.4; Tone.NO_PRESSURE -> if (favourite) -0.1 else 0.45; Tone.CALM -> 0.15
                Tone.DEMAND_MORE -> if (favourite) 0.35 else -0.3; Tone.PRAISE -> -0.4 }
        }
        Stage.FULL -> when {
            scoreDiff > 0 -> when (tone) { Tone.PRAISE -> 0.6; Tone.CALM -> 0.3; Tone.CRITICISE -> -0.7; Tone.DEMAND_MORE -> 0.0; else -> 0.2 }
            scoreDiff == 0 -> when (tone) { Tone.CALM -> 0.3; Tone.PRAISE -> if (favourite) -0.2 else 0.45
                Tone.CRITICISE -> if (favourite) 0.15 else -0.5; Tone.DEMAND_MORE -> if (favourite) 0.3 else -0.2; else -> 0.1 }
            else -> when (tone) { Tone.CRITICISE -> if (favourite) 0.35 else -0.6; Tone.ASSERTIVE -> if (favourite) 0.3 else 0.0
                Tone.NO_PRESSURE -> if (favourite) -0.2 else 0.45; Tone.CALM -> 0.3; Tone.PRAISE -> if (favourite) -0.5 else 0.25; else -> 0.0 }
        }
    }

    fun react(world: World, players: List<Player>, tone: Tone, stage: Stage, scoreDiff: Int, favourite: Boolean, salt: Long): List<Reaction> {
        val b = base(tone, stage, scoreDiff, favourite)
        return players.map { p ->
            val rng = Rng.stream(world.seed, "talk", p.id, world.today.toLong(), salt)
            val pers = p.personality
            var v = b
            if ((tone == Tone.CRITICISE || tone == Tone.ASSERTIVE || tone == Tone.DEMAND_MORE) && pers.temperament <= 7) v -= 0.3
            if (tone == Tone.CRITICISE && pers.professionalism >= 15) v += 0.2
            if (tone == Tone.PASSIONATE && pers.pressure <= 7) v -= 0.25
            if (tone == Tone.NO_PRESSURE && pers.ambition >= 16) v -= 0.15
            if (p.morale < 40) v += if (tone == Tone.PRAISE || tone == Tone.CALM) 0.15 else -0.1
            v += (p.relationship - 50) / 250.0
            v += rng.gaussian() * 0.15
            Reaction(p.id, v.coerceIn(-1.0, 1.0))
        }
    }

    /** Apply reactions to morale (persisting) and return the in-match motivation per player. */
    fun applyMorale(world: World, reactions: List<Reaction>, stage: Stage): Map<Long, Double> {
        for (r in reactions) world.players[r.playerId]?.let { it.morale = (it.morale + Math.round(r.value * (if (stage == Stage.FULL) 8 else 4)).toInt()).coerceIn(5, 100) }
        return reactions.associate { it.playerId to it.value }
    }

    /** Assistant's suggestion: the tone with the best base effect in this situation. */
    fun suggest(stage: Stage, scoreDiff: Int, favourite: Boolean): Tone = Tone.entries.maxBy { base(it, stage, scoreDiff, favourite) }
}

/** Press conferences before and after the user's matches. */
object Press {
    enum class Answer(val id: String, val en: String) {
        CONFIDENT("Percaya diri", "Confident"),
        CAUTIOUS("Hati-hati", "Cautious"),
        PRAISE("Memuji", "Praise"),
        CRITICAL("Kritis", "Critical"),
        DEFLECT("Menghindar", "Deflect");
        fun label(lang: Lang) = if (lang == Lang.ID) id else en
    }

    fun preMatch(world: World, f: Fixture): PressConference {
        val uid = world.manager.clubId
        val opp = world.club(if (f.homeId == uid) f.awayId else f.homeId)
        val club = world.club(uid)
        val comp = world.comp(f.compId)
        val squad = world.squad(uid)
        // Context-driven candidates (most specific first); the opening question is always about the opponent.
        val cands = mutableListOf<PressQuestion>()
        val losses = club.form.takeLast(3).count { it == 'L' }
        if (losses >= 2) cands += PressQuestion(2, "press_pre_badform", listOf(losses.toString()))
        else if (club.form.takeLast(3).count { it == 'W' } >= 3) cands += PressQuestion(2, "press_pre_goodform")
        val injured = squad.count { !it.reserve && it.injuryDays > 0 }
        if (injured >= 3) cands += PressQuestion(5, "press_pre_injuries", listOf(injured.toString()))
        if (comp.type != CompType.LEAGUE) cands += PressQuestion(6, "press_pre_cup", listOf(comp.name(world.settings.lang)))
        else {
            val pu = fm.engine.competition.CompetitionEngine.position(world, uid); val po = fm.engine.competition.CompetitionEngine.position(world, opp.id)
            if (pu in 1..3 && po in 1..3) cands += PressQuestion(7, "press_pre_bigmatch", listOf(opp.name))
        }
        squad.filter { it.age(world.today) <= 21 && it.season.apps >= 3 && it.season.avgRating >= 7.0 }.maxByOrNull { it.season.avgRating }
            ?.let { cands += PressQuestion(8, "press_pre_youngster", listOf(it.name), it.id) }
        if (fm.engine.market.Transfers.windowOpen(world)) squad.filter { it.wantsOut || it.personality.ambition >= 16 }.maxByOrNull { it.ca }
            ?.let { cands += PressQuestion(9, "press_pre_rumour", listOf(it.name), it.id) }
        if (club.boardConfidence < 35) cands += PressQuestion(10, "press_pre_pressure")
        squad.maxByOrNull { it.ca }?.let { cands += PressQuestion(3, "press_pre_player", listOf(it.name), it.id) }
        if (club.expectedPosition <= 3) cands += PressQuestion(4, "press_pre_title")
        val rng = Rng.stream(world.seed, "pressq", f.id)
        val chosen = (cands.take(2) + rng.shuffled(cands.drop(2))).distinctBy { it.key }.take(2)
        return PressConference(world.today, true, f.id, listOf(PressQuestion(1, "press_pre_opponent", listOf(opp.name))) + chosen)
    }

    fun postMatch(world: World, f: Fixture, motmId: Long): PressConference {
        val uid = world.manager.clubId
        val home = f.homeId == uid
        val gf = if (home) f.homeGoals else f.awayGoals
        val ga = if (home) f.awayGoals else f.homeGoals
        val opp = world.club(if (home) f.awayId else f.homeId)
        val qs = mutableListOf(PressQuestion(1, when { gf > ga -> "press_post_win"; gf == ga -> "press_post_draw"; else -> "press_post_loss" }, listOf(opp.name, "$gf-$ga")))
        world.players[motmId]?.takeIf { it.playingFor == uid }?.let { qs += PressQuestion(2, "press_post_player", listOf(it.name), it.id) }
        val myTeam = if (home) "0" else "1"
        val red = f.events.map { it.split("|") }.firstOrNull { it.size >= 4 && it[1] == "RED" && it[2] == myTeam }?.get(3)?.toLongOrNull()
        world.players[red ?: -1]?.let { qs += PressQuestion(4, "press_post_red", listOf(it.name), it.id) }
        if (ga - gf >= 3) qs += PressQuestion(5, "press_post_heavy", listOf("$gf-$ga"))
        if (gf > ga && ga == 0) qs += PressQuestion(6, "press_post_cleansheet")
        val weak = world.squad(uid).filter { it.season.apps >= 3 && it.season.avgRating < 6.4 }.minByOrNull { it.season.avgRating }
        if (weak != null) qs += PressQuestion(3, "press_post_weak", listOf(weak.name), weak.id)
        return PressConference(world.today, false, f.id, qs.distinctBy { it.key }.take(3))
    }

    /** Effect of an answer; returns the localisation key of the media reaction. */
    fun answer(world: World, q: PressQuestion, a: Answer): String {
        val p = world.players[q.playerId]
        val club = world.userClub
        when (a) {
            Answer.CONFIDENT -> { world.mediaHype = (world.mediaHype + 5).coerceAtMost(100); club?.let { c -> world.squad(c.id).forEach { it.morale = (it.morale + 1).coerceAtMost(100) } } }
            Answer.CAUTIOUS -> world.mediaHype = (world.mediaHype - 3).coerceAtLeast(0)
            Answer.PRAISE -> p?.let { it.morale = (it.morale + 6).coerceAtMost(100); it.relationship = (it.relationship + 5).coerceAtMost(100) }
                ?: club?.let { c -> world.squad(c.id).forEach { it.morale = (it.morale + 2).coerceAtMost(100) } }
            Answer.CRITICAL -> p?.let {
                val d = if (it.personality.professionalism >= 15) 2 else -8
                it.morale = (it.morale + d).coerceIn(5, 100); it.relationship = (it.relationship - 8).coerceAtLeast(0)
            } ?: club?.let { c -> world.squad(c.id).forEach { it.morale = (it.morale - 2).coerceAtLeast(5) }; c.boardConfidence = (c.boardConfidence + 1).coerceAtMost(100) }
            Answer.DEFLECT -> world.mediaHype = (world.mediaHype - 1).coerceAtLeast(0)
        }
        // Question-specific consequences.
        when (q.key) {
            "press_pre_rumour" -> p?.let {
                if (a == Answer.CONFIDENT) { it.relationship = (it.relationship + 4).coerceAtMost(100); it.morale = (it.morale + 3).coerceAtMost(100) }
                if (a == Answer.CRITICAL) { it.unhappyWeeks += 2 }
            }
            "press_pre_youngster" -> if (a == Answer.PRAISE) p?.let { it.morale = (it.morale + 4).coerceAtMost(100) }
            "press_pre_pressure" -> club?.let { c -> when (a) { Answer.CONFIDENT -> c.boardConfidence = (c.boardConfidence + 2).coerceAtMost(100)
                Answer.CRITICAL -> c.boardConfidence = (c.boardConfidence - 3).coerceAtLeast(0); else -> {} } }
            "press_post_red" -> if (a == Answer.CRITICAL) p?.let { it.morale = (it.morale - 4).coerceAtLeast(5) }
            "press_post_heavy" -> if (a == Answer.CRITICAL) club?.let { c -> world.squad(c.id).forEach { it.morale = (it.morale - 2).coerceAtLeast(5) } }
            "press_pre_bigmatch", "press_pre_cup" -> if (a == Answer.CONFIDENT) world.mediaHype = (world.mediaHype + 3).coerceAtMost(100)
        }
        val press = world.pendingPress
        if (press != null) { press.answered++; if (press.answered >= press.questions.size) world.pendingPress = null }
        return "press_reaction_" + a.name.lowercase()
    }
}
