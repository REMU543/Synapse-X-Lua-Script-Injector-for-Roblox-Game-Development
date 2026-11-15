package fm.engine.save

import fm.core.World
import fm.engine.world.WorldGenerator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import java.io.*
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Career save slots.
 *
 * File layout: magic "FMA1" | int schemaVersion | long crc32 | int headerLength | header JSON |
 * gzip(CBOR(World)). Writes are atomic (temp file + rename) and the previous three saves of a
 * slot are kept as rotating backups. Loading verifies magic, version and checksum, then runs
 * migrations for older schema versions.
 */
@OptIn(ExperimentalSerializationApi::class)
class SaveStore(private val dir: File) {
    @Serializable
    data class SlotInfo(
        val slot: String,
        val managerName: String,
        val clubName: String,
        val day: Int,
        val season: Int,
        val savedAtMillis: Long,
        val schemaVersion: Int,
        val sizeBytes: Long = 0,
    )

    class CorruptSaveException(msg: String) : IOException(msg)

    private val cbor = Cbor { ignoreUnknownKeys = true }
    private val json = Json { ignoreUnknownKeys = true }

    init { dir.mkdirs() }

    private fun file(slot: String) = File(dir, "$slot.fmsave")

    fun save(world: World, slot: String, nowMillis: Long = System.currentTimeMillis()): SlotInfo {
        require(slot.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "invalid slot name" }
        val body = ByteArrayOutputStream()
        GZIPOutputStream(body).use { it.write(cbor.encodeToByteArray(world)) }
        val bytes = body.toByteArray()
        val crc = CRC32().apply { update(bytes) }.value
        val info = SlotInfo(slot, world.manager.name, world.userClub?.name ?: "-", world.today, world.season, nowMillis, world.schemaVersion)
        val header = json.encodeToString(SlotInfo.serializer(), info).toByteArray(Charsets.UTF_8)
        val target = file(slot)
        val tmp = File(dir, "$slot.fmsave.tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
            out.writeBytes("FMA1")
            out.writeInt(world.schemaVersion)
            out.writeLong(crc)
            out.writeInt(header.size)
            out.write(header)
            out.write(bytes)
            out.flush()
        }
        // Rotate backups: .bak2 -> .bak3, .bak1 -> .bak2, current -> .bak1
        if (target.exists()) {
            for (i in 2 downTo 1) {
                val from = File(dir, "$slot.fmsave.bak$i")
                if (from.exists()) from.renameTo(File(dir, "$slot.fmsave.bak${i + 1}"))
            }
            target.renameTo(File(dir, "$slot.fmsave.bak1"))
        }
        if (!tmp.renameTo(target)) throw IOException("could not finalise save")
        return info.copy(sizeBytes = target.length())
    }

    fun load(slot: String): World = loadFile(file(slot))

    /** Loads the newest valid file: the slot itself, else its backups. */
    fun loadWithRecovery(slot: String): Pair<World, String> {
        val candidates = listOf(file(slot)) + (1..3).map { File(dir, "$slot.fmsave.bak$it") }
        var last: Exception? = null
        for (f in candidates.filter { it.exists() }) {
            try { return loadFile(f) to f.name } catch (e: Exception) { last = e }
        }
        throw last ?: FileNotFoundException(slot)
    }

    fun loadFile(f: File): World {
        DataInputStream(BufferedInputStream(FileInputStream(f))).use { inp ->
            val magic = ByteArray(4).also { inp.readFully(it) }
            if (String(magic) != "FMA1") throw CorruptSaveException("not a save file")
            val version = inp.readInt()
            if (version > WorldGenerator.SCHEMA_VERSION) throw CorruptSaveException("save from a newer version ($version)")
            val crc = inp.readLong()
            val hlen = inp.readInt()
            if (hlen < 0 || hlen > 1_000_000) throw CorruptSaveException("bad header")
            inp.readFully(ByteArray(hlen))
            val body = inp.readBytes()
            val actual = CRC32().apply { update(body) }.value
            if (actual != crc) throw CorruptSaveException("checksum mismatch")
            val raw = GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
            val world = cbor.decodeFromByteArray<World>(raw)
            return Migrations.migrate(world, version)
        }
    }

    fun info(slot: String): SlotInfo? = readInfo(file(slot))

    private fun readInfo(f: File): SlotInfo? = try {
        DataInputStream(BufferedInputStream(FileInputStream(f))).use { inp ->
            val magic = ByteArray(4).also { inp.readFully(it) }
            if (String(magic) != "FMA1") return null
            inp.readInt(); inp.readLong()
            val h = ByteArray(inp.readInt()).also { inp.readFully(it) }
            json.decodeFromString(SlotInfo.serializer(), String(h, Charsets.UTF_8)).copy(sizeBytes = f.length())
        }
    } catch (e: Exception) { null }

    fun list(): List<SlotInfo> = (dir.listFiles { f -> f.name.endsWith(".fmsave") }?.toList() ?: emptyList())
        .mapNotNull { readInfo(it) }.sortedByDescending { it.savedAtMillis }

    fun delete(slot: String) {
        file(slot).delete()
        (1..3).forEach { File(dir, "$slot.fmsave.bak$it").delete() }
    }
}

/** Save-format migrations. Each step upgrades a world from version N to N+1. */
object Migrations {
    private val steps: Map<Int, (World) -> World> = mapOf(
        // 1 -> 2: dynamics (relationship, happiness, promises, press). New fields have defaults, nothing to convert.
        1 to { w -> w },
        // 2 -> 3: roles/duties/player instructions, weekly training, individual training. Defaults only.
        2 to { w -> w },
        // 3 -> 4: national teams & international fixtures. Created lazily on first use.
        3 to { w -> w },
    )

    fun migrate(world: World, fromVersion: Int): World {
        var w = world
        var v = fromVersion
        while (v < WorldGenerator.SCHEMA_VERSION) {
            w = steps[v]?.invoke(w) ?: w
            v++
        }
        WorldGenerator.recomputeAll(w)
        return w
    }
}
