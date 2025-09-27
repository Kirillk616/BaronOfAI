package wadtool

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * WadReader - reads a WAD file and saves each map (level) as a JSON file.
 * Similar in spirit to WadWriter, but performing read and export per-level.
 *
 * Output files are created under the provided output directory (default WADTool\\data)
 * and are named as: <wadFileNameWithoutExt>_<levelIndex>.json
 */
class WadReader(private val filePath: String) {

    data class WadHeader(val identification: String, val numLumps: Int, val infoTableOffset: Int)
    data class DirectoryEntry(val filePos: Int, val size: Int, val name: String)

    data class LevelModel(
        val name: String,
        val vertexes: List<Vertex> = emptyList(),
        val lineDefs: List<LineDef> = emptyList(),
        val sideDefs: List<SideDef> = emptyList(),
        val sectors: List<Sector> = emptyList(),
        val things: List<Thing> = emptyList(),
        val nodes: List<Node> = emptyList(),
        val subSectors: List<SubSector> = emptyList(),
        val segs: List<Seg> = emptyList()
    )

    private lateinit var header: WadHeader
    private val directory = mutableListOf<DirectoryEntry>()

    fun exportLevelsAsJson(outputDir: String = "WADTool\\data"): Boolean {
        return try {
            RandomAccessFile(filePath, "r").use { raf ->
                readHeader(raf)
                readDirectory(raf)

                val levels = detectLevels()
                if (levels.isEmpty()) {
                    println("No levels detected in WAD: $filePath")
                    return@use
                }

                val outDir = File(outputDir)
                outDir.mkdirs()

                val baseName = File(filePath).nameWithoutExtension
                val mapper = jacksonObjectMapper().writerWithDefaultPrettyPrinter()

                levels.forEachIndexed { index, levelName ->
                    val model = readLevel(raf, levelName)
                    val outFile = File(outDir, "${baseName}_${index + 1}.json")
                    outFile.writeText(mapper.writeValueAsString(model))
                    println("Saved level ${levelName} to ${outFile.absolutePath}")
                }
            }
            true
        } catch (t: Throwable) {
            System.err.println("WadReader failed: ${t.message}")
            t.printStackTrace()
            false
        }
    }

    private fun readHeader(raf: RandomAccessFile) {
        val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        raf.read(buffer.array())
        val idBytes = ByteArray(4)
        buffer.get(idBytes)
        val identification = String(idBytes, StandardCharsets.US_ASCII)
        val numLumps = buffer.getInt()
        val infoTableOffset = buffer.getInt()
        header = WadHeader(identification, numLumps, infoTableOffset)
    }

    private fun readDirectory(raf: RandomAccessFile) {
        raf.seek(header.infoTableOffset.toLong())
        repeat(header.numLumps) {
            val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())
            val filePos = buffer.getInt()
            val size = buffer.getInt()
            val nameBytes = ByteArray(8)
            buffer.get(nameBytes)
            val name = String(nameBytes, StandardCharsets.US_ASCII).trim { it <= ' ' }
            directory.add(DirectoryEntry(filePos, size, name))
        }
    }

    private fun detectLevels(): List<String> {
        val result = mutableListOf<String>()
        val mapRegex = Regex("MAP[0-9]{2}")
        val eXmYRegex = Regex("E[0-9]M[0-9]")
        directory.forEach { de ->
            if (mapRegex.matches(de.name) || eXmYRegex.matches(de.name)) {
                result.add(de.name)
            }
        }
        return result
    }

    private fun readLevel(raf: RandomAccessFile, levelMarker: String): LevelModel {
        // Lumps that typically follow a level marker
        val wanted = mapOf(
            "THINGS" to ::parseThings,
            "LINEDEFS" to ::parseLineDefs,
            "SIDEDEFS" to ::parseSideDefs,
            "VERTEXES" to ::parseVertexes,
            "SEGS" to ::parseSegs,
            "SSECTORS" to ::parseSubSectors,
            "NODES" to ::parseNodes,
            "SECTORS" to ::parseSectors,
            // REJECT and BLOCKMAP are ignored for model export
        )

        // Find the index of the marker and then gather following lumps until next marker or end
        val idx = directory.indexOfFirst { it.name == levelMarker }
        if (idx == -1) return LevelModel(levelMarker)

        var i = idx + 1
        var vertexes: List<Vertex> = emptyList()
        var lineDefs: List<LineDef> = emptyList()
        var sideDefs: List<SideDef> = emptyList()
        var sectors: List<Sector> = emptyList()
        var things: List<Thing> = emptyList()
        var nodes: List<Node> = emptyList()
        var ssectors: List<SubSector> = emptyList()
        var segs: List<Seg> = emptyList()

        val mapRegex = Regex("MAP[0-9]{2}")
        val eXmYRegex = Regex("E[0-9]M[0-9]")

        while (i < directory.size) {
            val de = directory[i]
            if (mapRegex.matches(de.name) || eXmYRegex.matches(de.name)) break
            when (de.name) {
                "VERTEXES" -> vertexes = parseVertexes(raf, de)
                "LINEDEFS" -> lineDefs = parseLineDefs(raf, de)
                "SIDEDEFS" -> sideDefs = parseSideDefs(raf, de)
                "SECTORS" -> sectors = parseSectors(raf, de)
                "THINGS" -> things = parseThings(raf, de)
                "NODES" -> nodes = parseNodes(raf, de)
                "SSECTORS" -> ssectors = parseSubSectors(raf, de)
                "SEGS" -> segs = parseSegs(raf, de)
            }
            i++
        }

        return LevelModel(
            name = levelMarker,
            vertexes = vertexes,
            lineDefs = lineDefs,
            sideDefs = sideDefs,
            sectors = sectors,
            things = things,
            nodes = nodes,
            subSectors = ssectors,
            segs = segs
        )
    }

    // Parsing helpers for each lump type, based on formats used in WadParser
    private fun parseVertexes(raf: RandomAccessFile, de: DirectoryEntry): List<Vertex> {
        if (de.size <= 0) return emptyList()
        val count = de.size / 4
        val list = ArrayList<Vertex>(count)
        raf.seek(de.filePos.toLong())
        repeat(count) {
            val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buf.array())
            val x = buf.getShort()
            val y = buf.getShort()
            list.add(Vertex(x, y))
        }
        return list
    }

    private fun parseLineDefs(raf: RandomAccessFile, de: DirectoryEntry): List<LineDef> {
        if (de.size <= 0) return emptyList()
        val count = de.size / 14
        val list = ArrayList<LineDef>(count)
        raf.seek(de.filePos.toLong())
        repeat(count) {
            val buf = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buf.array())
            val start = buf.getShort().toInt() and 0xFFFF
            val end = buf.getShort().toInt() and 0xFFFF
            val flags = buf.getShort()
            val special = buf.getShort()
            val tag = buf.getShort()
            val right = buf.getShort().toInt() and 0xFFFF
            val left = buf.getShort().toInt() and 0xFFFF
            list.add(LineDef(start, end, flags, special, tag, right, left))
        }
        return list
    }

    private fun readNameFrom(buffer: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return String(bytes, StandardCharsets.US_ASCII).trim { it <= ' ' }
    }

    private fun parseSideDefs(raf: RandomAccessFile, de: DirectoryEntry): List<SideDef> {
        if (de.size <= 0) return emptyList()
        val count = de.size / 30
        val list = ArrayList<SideDef>(count)
        raf.seek(de.filePos.toLong())
        repeat(count) {
            val buf = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buf.array())
            val xOff = buf.getShort()
            val yOff = buf.getShort()
            val upper = readNameFrom(buf, 8)
            val lower = readNameFrom(buf, 8)
            val middle = readNameFrom(buf, 8)
            val sector = buf.getShort().toInt() and 0xFFFF
            list.add(SideDef(xOff, yOff, upper, lower, middle, sector))
        }
        return list
    }

    private fun parseSectors(raf: RandomAccessFile, de: DirectoryEntry): List<Sector> {
        if (de.size <= 0) return emptyList()
        val count = de.size / 26
        val list = ArrayList<Sector>(count)
        raf.seek(de.filePos.toLong())
        repeat(count) {
            val buf = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buf.array())
            val floor = buf.getShort()
            val ceil = buf.getShort()
            val floorTex = readNameFrom(buf, 8)
            val ceilTex = readNameFrom(buf, 8)
            val light = buf.getShort()
            val special = buf.getShort()
            val tag = buf.getShort()
            list.add(Sector(floor, ceil, floorTex, ceilTex, light, special, tag))
        }
        return list
    }

    private fun parseThings(raf: RandomAccessFile, de: DirectoryEntry): List<Thing> {
        if (de.size <= 0) return emptyList()
        val count = de.size / 10
        val list = ArrayList<Thing>(count)
        raf.seek(de.filePos.toLong())
        repeat(count) {
            val buf = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buf.array())
            val x = buf.getShort()
            val y = buf.getShort()
            val angle = buf.getShort()
            val type = buf.getShort()
            val flags = buf.getShort()
            list.add(Thing(x, y, angle, type, flags))
        }
        return list
    }

    private fun parseNodes(raf: RandomAccessFile, de: DirectoryEntry): List<Node> {
        if (de.size <= 0) return emptyList()
        val count = de.size / 28
        val list = ArrayList<Node>(count)
        raf.seek(de.filePos.toLong())
        repeat(count) {
            val buf = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buf.array())
            val xPart = buf.getShort()
            val yPart = buf.getShort()
            val xChange = buf.getShort()
            val yChange = buf.getShort()
            val rightTop = buf.getShort()
            val rightBottom = buf.getShort()
            val rightLeft = buf.getShort()
            val rightRight = buf.getShort()
            val leftTop = buf.getShort()
            val leftBottom = buf.getShort()
            val leftLeft = buf.getShort()
            val leftRight = buf.getShort()
            val rightChild = buf.getShort().toInt() and 0xFFFF
            val leftChild = buf.getShort().toInt() and 0xFFFF
            list.add(
                Node(
                    xPart, yPart, xChange, yChange,
                    rightTop, rightBottom, rightLeft, rightRight,
                    leftTop, leftBottom, leftLeft, leftRight,
                    rightChild, leftChild
                )
            )
        }
        return list
    }

    private fun parseSubSectors(raf: RandomAccessFile, de: DirectoryEntry): List<SubSector> {
        if (de.size <= 0) return emptyList()
        val count = de.size / 4
        val list = ArrayList<SubSector>(count)
        raf.seek(de.filePos.toLong())
        repeat(count) {
            val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buf.array())
            val segCount = buf.getShort()
            val firstSeg = buf.getShort().toInt() and 0xFFFF
            list.add(SubSector(segCount, firstSeg))
        }
        return list
    }

    private fun parseSegs(raf: RandomAccessFile, de: DirectoryEntry): List<Seg> {
        if (de.size <= 0) return emptyList()
        val count = de.size / 12
        val list = ArrayList<Seg>(count)
        raf.seek(de.filePos.toLong())
        repeat(count) {
            val buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buf.array())
            val start = buf.getShort().toInt() and 0xFFFF
            val end = buf.getShort().toInt() and 0xFFFF
            val angle = buf.getShort()
            val lineDef = buf.getShort().toInt() and 0xFFFF
            val direction = buf.getShort()
            val offset = buf.getShort()
            list.add(Seg(start, end, angle, lineDef, direction, offset))
        }
        return list
    }
}

// Simple test utility function to trigger WadReader export from a given WAD file name.
// If a relative name is provided and the file is not found, it will look under WADTool\\data.
fun main() {
    val wadFileName = "C:/Program Files (x86)/Steam/steamapps/common/Doom 2/gzdoom/DOOM2.WAD"
    val outputDir = "WADTool/data"
    val direct = File(wadFileName)
    val resolved = if (direct.exists()) direct else File("WADTool\\data\\$wadFileName")
    if (!resolved.exists()) {
        println("WadReader test: file not found: ${resolved.absolutePath}")
        return
    }
    println("WadReader test: exporting levels from ${resolved.absolutePath} to $outputDir")
    if (!WadReader(resolved.path).exportLevelsAsJson(outputDir))
        println("WadReader export failed")
}
