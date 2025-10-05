package wadtool.storage

import java.io.File
import java.time.Instant

/**
 * Abstraction for persisting and retrieving generated levels. Designed to allow S3 implementation later.
 */
interface LevelStorage {
    data class LevelMeta(
        val id: String,
        val prompt: String,
        val createdAt: Instant
    )

    /**
     * Save a new level into storage. Returns generated level id.
     */
    fun save(prompt: String, wadFile: File, svgContent: String): String

    /**
     * List levels, most recent first.
     */
    fun list(limit: Int = 50): List<LevelMeta>

    /**
     * Retrieve SVG text for a level.
     */
    fun getSvg(id: String): String?

    /**
     * Retrieve WAD file for a level.
     */
    fun getWad(id: String): File?

    /**
     * Retrieve prompt for a level.
     */
    fun getPrompt(id: String): String?
}