package wadtool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Parser for DOOM/DOOM2 WAD files.
 * Handles reading and parsing the WAD file format.
 */
class WadParser(private val filePath: String) {

    // WAD file header structure
    data class WadHeader(
        val identification: String, // "IWAD" or "PWAD"
        val numLumps: Int,          // Number of lumps (data entries)
        val infoTableOffset: Int    // Offset to the directory
    )

    // Directory entry structure
    data class DirectoryEntry(
        val filePos: Int,    // Offset to the start of the lump data
        val size: Int,       // Size of the lump in bytes
        val name: String     // Name of the lump (8 characters)
    )

    private lateinit var wadHeader: WadHeader
    private val directoryEntries = mutableListOf<DirectoryEntry>()
    
    // Data structures from the WAD file
    val vertexes = mutableListOf<Vertex>()
    val lineDefs = mutableListOf<LineDef>()
    val sideDefs = mutableListOf<SideDef>()
    val sectors = mutableListOf<Sector>()
    val things = mutableListOf<Thing>()
    val nodes = mutableListOf<Node>()
    val subSectors = mutableListOf<SubSector>()
    val segs = mutableListOf<Seg>()

    /**
     * Parse the WAD file and load all data structures.
     */
    fun parse(): Boolean {
        try {
            RandomAccessFile(filePath, "r").use { raf ->
                // Read the WAD header
                readHeader(raf)
                
                // Read the directory entries
                readDirectory(raf)
                
                // Parse each data structure
                parseVertexes(raf)
                parseLineDefs(raf)
                parseSideDefs(raf)
                parseSectors(raf)
                parseThings(raf)
                parseNodes(raf)
                parseSubSectors(raf)
                parseSegs(raf)
            }
            return true
        } catch (e: Exception) {
            println("Error parsing WAD file: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Read the WAD file header.
     */
    private fun readHeader(raf: RandomAccessFile) {
        val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        raf.read(buffer.array())
        
        val idBytes = ByteArray(4)
        buffer.get(idBytes)
        val identification = String(idBytes, StandardCharsets.US_ASCII)
        val numLumps = buffer.getInt()
        val infoTableOffset = buffer.getInt()
        
        wadHeader = WadHeader(identification, numLumps, infoTableOffset)
        println("WAD Header: $wadHeader")
    }

    /**
     * Read the directory entries.
     */
    private fun readDirectory(raf: RandomAccessFile) {
        raf.seek(wadHeader.infoTableOffset.toLong())

        (0 until wadHeader.numLumps).forEach { i ->
            val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())

            val filePos = buffer.getInt()
            val size = buffer.getInt()

            val nameBytes = ByteArray(8)
            buffer.get(nameBytes)
            // Convert to string and trim null characters
            val name = String(nameBytes, StandardCharsets.US_ASCII).trim { it <= ' ' }

            directoryEntries.add(DirectoryEntry(filePos, size, name))
        }
        
        println("Read ${directoryEntries.size} directory entries")
    }

    /**
     * Find a directory entry by name.
     */
    private fun findDirectoryEntry(name: String): DirectoryEntry? {
        return directoryEntries.find { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Parse VERTEXES data.
     */
    private fun parseVertexes(raf: RandomAccessFile) {
        val entry = findDirectoryEntry("VERTEXES") ?: return
        raf.seek(entry.filePos.toLong())
        
        val vertexSize = 4 // 2 shorts (x, y)
        val numVertexes = entry.size / vertexSize

        (0 until numVertexes).forEach { i ->
            val buffer = ByteBuffer.allocate(vertexSize).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())

            val x = buffer.getShort()
            val y = buffer.getShort()

            vertexes.add(Vertex(x, y))
        }
        
        println("Parsed ${vertexes.size} vertexes")
    }

    /**
     * Parse LINEDEFS data.
     */
    private fun parseLineDefs(raf: RandomAccessFile) {
        val entry = findDirectoryEntry("LINEDEFS") ?: return
        raf.seek(entry.filePos.toLong())
        
        val lineDefSize = 14 // 2 shorts + 5 shorts
        val numLineDefs = entry.size / lineDefSize

        (0 until numLineDefs).forEach { i ->
            val buffer = ByteBuffer.allocate(lineDefSize).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())

            val startVertex = buffer.getShort().toInt() and 0xFFFF
            val endVertex = buffer.getShort().toInt() and 0xFFFF
            val flags = buffer.getShort()
            val specialType = buffer.getShort()
            val sectorTag = buffer.getShort()
            val rightSideDef = buffer.getShort().toInt() and 0xFFFF
            val leftSideDef = buffer.getShort().toInt() and 0xFFFF

            lineDefs.add(LineDef(startVertex, endVertex, flags, specialType, sectorTag, rightSideDef, leftSideDef))
        }
        
        println("Parsed ${lineDefs.size} linedefs")
    }

    /**
     * Parse SIDEDEFS data.
     */
    private fun parseSideDefs(raf: RandomAccessFile) {
        val entry = findDirectoryEntry("SIDEDEFS") ?: return
        raf.seek(entry.filePos.toLong())
        
        val sideDefSize = 30 // 2 shorts + 3 * 8 chars + 1 short
        val numSideDefs = entry.size / sideDefSize

        (0 until numSideDefs).forEach { i ->
            val buffer = ByteBuffer.allocate(sideDefSize).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())

            val xOffset = buffer.getShort()
            val yOffset = buffer.getShort()

            val upperTextureBytes = ByteArray(8)
            buffer.get(upperTextureBytes)
            val upperTexture = String(upperTextureBytes, StandardCharsets.US_ASCII).trim { it <= ' ' }

            val lowerTextureBytes = ByteArray(8)
            buffer.get(lowerTextureBytes)
            val lowerTexture = String(lowerTextureBytes, StandardCharsets.US_ASCII).trim { it <= ' ' }

            val middleTextureBytes = ByteArray(8)
            buffer.get(middleTextureBytes)
            val middleTexture = String(middleTextureBytes, StandardCharsets.US_ASCII).trim { it <= ' ' }

            val sector = buffer.getShort().toInt() and 0xFFFF

            sideDefs.add(SideDef(xOffset, yOffset, upperTexture, lowerTexture, middleTexture, sector))
        }
        
        println("Parsed ${sideDefs.size} sidedefs")
    }

    /**
     * Parse SECTORS data.
     */
    private fun parseSectors(raf: RandomAccessFile) {
        val entry = findDirectoryEntry("SECTORS") ?: return
        raf.seek(entry.filePos.toLong())
        
        val sectorSize = 26 // 2 shorts + 2 * 8 chars + 3 shorts
        val numSectors = entry.size / sectorSize

        (0 until numSectors).forEach { i ->
            val buffer = ByteBuffer.allocate(sectorSize).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())

            val floorHeight = buffer.getShort()
            val ceilingHeight = buffer.getShort()

            val floorTextureBytes = ByteArray(8)
            buffer.get(floorTextureBytes)
            val floorTexture = String(floorTextureBytes, StandardCharsets.US_ASCII).trim { it <= ' ' }

            val ceilingTextureBytes = ByteArray(8)
            buffer.get(ceilingTextureBytes)
            val ceilingTexture = String(ceilingTextureBytes, StandardCharsets.US_ASCII).trim { it <= ' ' }

            val lightLevel = buffer.getShort()
            val specialType = buffer.getShort()
            val tag = buffer.getShort()

            sectors.add(Sector(floorHeight, ceilingHeight, floorTexture, ceilingTexture, lightLevel, specialType, tag))
        }
        
        println("Parsed ${sectors.size} sectors")
    }

    /**
     * Parse THINGS data.
     */
    private fun parseThings(raf: RandomAccessFile) {
        val entry = findDirectoryEntry("THINGS") ?: return
        raf.seek(entry.filePos.toLong())
        
        val thingSize = 10 // 5 shorts
        val numThings = entry.size / thingSize

        (0 until numThings).forEach { i ->
            val buffer = ByteBuffer.allocate(thingSize).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())

            val x = buffer.getShort()
            val y = buffer.getShort()
            val angle = buffer.getShort()
            val type = buffer.getShort()
            val flags = buffer.getShort()

            things.add(Thing(x, y, angle, type, flags))
        }
        
        println("Parsed ${things.size} things")
    }

    /**
     * Parse NODES data.
     */
    private fun parseNodes(raf: RandomAccessFile) {
        val entry = findDirectoryEntry("NODES") ?: return
        raf.seek(entry.filePos.toLong())
        
        val nodeSize = 28 // 12 shorts + 2 shorts as ints
        val numNodes = entry.size / nodeSize

        (0 until numNodes).forEach { i ->
            val buffer = ByteBuffer.allocate(nodeSize).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())

            val xPartition = buffer.getShort()
            val yPartition = buffer.getShort()
            val xChange = buffer.getShort()
            val yChange = buffer.getShort()

            val rightBoxTop = buffer.getShort()
            val rightBoxBottom = buffer.getShort()
            val rightBoxLeft = buffer.getShort()
            val rightBoxRight = buffer.getShort()

            val leftBoxTop = buffer.getShort()
            val leftBoxBottom = buffer.getShort()
            val leftBoxLeft = buffer.getShort()
            val leftBoxRight = buffer.getShort()

            val rightChild = buffer.getShort().toInt() and 0xFFFF
            val leftChild = buffer.getShort().toInt() and 0xFFFF

            nodes.add(Node(
                xPartition, yPartition, xChange, yChange,
                rightBoxTop, rightBoxBottom, rightBoxLeft, rightBoxRight,
                leftBoxTop, leftBoxBottom, leftBoxLeft, leftBoxRight,
                rightChild, leftChild
            ))
        }
        
        println("Parsed ${nodes.size} nodes")
    }

    /**
     * Parse SSECTORS data.
     */
    private fun parseSubSectors(raf: RandomAccessFile) {
        val entry = findDirectoryEntry("SSECTORS") ?: return
        raf.seek(entry.filePos.toLong())
        
        val subSectorSize = 4 // 1 short + 1 short as int
        val numSubSectors = entry.size / subSectorSize

        (0 until numSubSectors).forEach { i ->
            val buffer = ByteBuffer.allocate(subSectorSize).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())

            val segCount = buffer.getShort()
            val firstSeg = buffer.getShort().toInt() and 0xFFFF

            subSectors.add(SubSector(segCount, firstSeg))
        }
        
        println("Parsed ${subSectors.size} subsectors")
    }

    /**
     * Parse SEGS data.
     */
    private fun parseSegs(raf: RandomAccessFile) {
        val entry = findDirectoryEntry("SEGS") ?: return
        raf.seek(entry.filePos.toLong())
        
        val segSize = 12 // 2 shorts as ints + 4 shorts
        val numSegs = entry.size / segSize

        (0 until numSegs).forEach { i ->
            val buffer = ByteBuffer.allocate(segSize).order(ByteOrder.LITTLE_ENDIAN)
            raf.read(buffer.array())

            val startVertex = buffer.getShort().toInt() and 0xFFFF
            val endVertex = buffer.getShort().toInt() and 0xFFFF
            val angle = buffer.getShort()
            val lineDef = buffer.getShort().toInt() and 0xFFFF
            val direction = buffer.getShort()
            val offset = buffer.getShort()

            segs.add(Seg(startVertex, endVertex, angle, lineDef, direction, offset))
        }
        
        println("Parsed ${segs.size} segs")
    }

    /**
     * Get a summary of the parsed WAD data.
     */
    fun getSummary(): String {
        return """
            WAD File: $filePath
            Type: ${wadHeader.identification}
            Directory Entries: ${directoryEntries.size}
            
            Data Structures:
            - Vertexes: ${vertexes.size}
            - LineDefs: ${lineDefs.size}
            - SideDefs: ${sideDefs.size}
            - Sectors: ${sectors.size}
            - Things: ${things.size}
            - Nodes: ${nodes.size}
            - SubSectors: ${subSectors.size}
            - Segs: ${segs.size}
        """.trimIndent()
    }
    
    /**
     * Generate an SVG file representing the level geometry.
     * The SVG will contain lines for all linedefs in the level.
     * 
     * @param outputPath The path where the SVG file should be saved. If null, saves to "WADTool/data/level.svg"
     * @return True if the SVG was generated successfully, false otherwise
     */
    fun generateSvg(outputPath: String? = null): Boolean {
        if (vertexes.isEmpty() || lineDefs.isEmpty()) {
            println("Cannot generate SVG: No vertices or linedefs found")
            return false
        }
        
        try {
            // Calculate bounds of the level
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            
            vertexes.forEach { vertex ->
                if (vertex.x < minX) minX = vertex.x.toInt()
                if (-vertex.y < minY) minY = -vertex.y.toInt()
                if (vertex.x > maxX) maxX = vertex.x.toInt()
                if (-vertex.y > maxY) maxY = -vertex.y.toInt()
            }
            
            // Add some padding
            val padding = 20
            minX -= padding
            minY -= padding
            maxX += padding
            maxY += padding
            
            // Calculate dimensions and scaling
            val width = maxX - minX
            val height = maxY - minY
            val svgWidth = 1200
            val svgHeight = 900
            val scaleX = svgWidth.toDouble() / width
            val scaleY = svgHeight.toDouble() / height
            val scale = minOf(scaleX, scaleY)
            
            // Start building SVG content
            val svgBuilder = StringBuilder()
            svgBuilder.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <svg width="$svgWidth" height="$svgHeight" xmlns="http://www.w3.org/2000/svg">
                  <rect width="100%" height="100%" fill="black"/>
                  <g transform="translate(${svgWidth/2}, ${svgHeight/2}) scale($scale) translate(${-(minX + maxX)/2}, ${-(minY + maxY)/2})">
            """.trimIndent())
            
            // Add lines for each linedef
            lineDefs.forEach { lineDef ->
                if (lineDef.startVertex < vertexes.size && lineDef.endVertex < vertexes.size) {
                    val startVertex = vertexes[lineDef.startVertex]
                    val endVertex = vertexes[lineDef.endVertex]
                    
                    svgBuilder.append("""
                        <line x1="${startVertex.x}" y1="${-startVertex.y}" x2="${endVertex.x}" y2="${-endVertex.y}" stroke="#00FF00" stroke-width="${2/scale}"/>
                    """.trimIndent())
                }
            }
            
            // Add player start positions (things)
            things.forEach { thing ->
                // Player start is type 1
                if (thing.type.toInt() == 1) {
                    svgBuilder.append("""
                        <circle cx="${thing.x}" cy="${-thing.y}" r="${8/scale}" fill="blue"/>
                        <line 
                            x1="${thing.x}" 
                            y1="${-thing.y}" 
                            x2="${thing.x + 20 * Math.cos(Math.toRadians(thing.angle.toDouble()))}" 
                            y2="${-thing.y - 20 * Math.sin(Math.toRadians(thing.angle.toDouble()))}" 
                            stroke="blue" 
                            stroke-width="${2/scale}"/>
                    """.trimIndent())
                }
            }
            
            // Close SVG
            svgBuilder.append("""
                  </g>
                </svg>
            """.trimIndent())
            
            // Write to file
            val finalOutputPath = outputPath ?: "WADTool/data/level.svg"
            File(finalOutputPath).writeText(svgBuilder.toString())
            
            println("SVG generated successfully: $finalOutputPath")
            return true
        } catch (e: Exception) {
            println("Error generating SVG: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}