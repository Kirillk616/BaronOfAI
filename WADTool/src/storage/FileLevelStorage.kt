package wadtool.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

class FileLevelStorage(
    private val root: File = File("WADTool\\data\\levels")
) : LevelStorage {

    init {
        if (!root.exists()) root.mkdirs()
    }

    override fun save(prompt: String, wadFile: File, svgContent: String): String {
        val id = generateId()
        val dir = File(root, id)
        dir.mkdirs()

        // Save prompt
        File(dir, "prompt.txt").writeText(prompt)
        // Save SVG
        File(dir, "map.svg").writeText(svgContent)
        // Copy WAD
        val wadTarget = File(dir, "map.wad")
        Files.copy(wadFile.toPath(), wadTarget.toPath(), StandardCopyOption.REPLACE_EXISTING)
        // Save createdAt marker
        File(dir, "created.txt").writeText(Instant.now().toString())
        return id
    }

    override fun list(limit: Int): List<LevelStorage.LevelMeta> {
        if (!root.exists()) return emptyList()
        val items = root.listFiles { f -> f.isDirectory }?.mapNotNull { dir ->
            val id = dir.name
            val prompt = File(dir, "prompt.txt").takeIf { it.exists() }?.readText() ?: return@mapNotNull null
            val createdAt = File(dir, "created.txt").takeIf { it.exists() }?.readText()?.let {
                runCatching { Instant.parse(it.trim()) }.getOrNull()
            } ?: Instant.ofEpochMilli(dir.lastModified())
            LevelStorage.LevelMeta(id, prompt, createdAt)
        }?.sortedByDescending { it.createdAt } ?: emptyList()
        return if (items.size > limit) items.subList(0, limit) else items
    }

    override fun getSvg(id: String): String? {
        val file = File(File(root, id), "map.svg")
        return if (file.exists()) file.readText() else null
    }

    override fun getWad(id: String): File? {
        val file = File(File(root, id), "map.wad")
        return if (file.exists()) file else null
    }

    override fun getPrompt(id: String): String? {
        val file = File(File(root, id), "prompt.txt")
        return if (file.exists()) file.readText() else null
    }

    private fun generateId(): String {
        // timestamp + short uuid for readability and uniqueness
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.ZonedDateTime.now())
        val short = UUID.randomUUID().toString().substring(0, 8)
        return "${ts}_${short}"
    }
}