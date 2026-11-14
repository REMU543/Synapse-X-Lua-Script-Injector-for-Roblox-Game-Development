package fm.engine

import fm.core.*
import fm.engine.world.WorldGenerator

object TestWorlds {
    fun manager() = ManagerProfile(
        firstName = "Budi", lastName = "Penguji", nationId = 1, birthDay = Cal.of(1985, 3, 3),
        qualification = "Lisensi A", playingExperience = "Amatir",
        style = ManagerStyle("4-4-2", 0.0, 0.5, 0.5, 0.5, 0.5), reputation = 30,
    )
    fun world(seed: Long = 42L): World = WorldGenerator.generate(seed, manager())
}
