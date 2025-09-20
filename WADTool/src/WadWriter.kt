package wadtool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Writer for DOOM/DOOM2 WAD files.
 * Handles writing WAD file format from model classes.
 */
class WadWriter(private val outputPath: String) {

    // Constants for WAD file structure
    companion object {
        const val WAD_HEADER_SIZE = 12 // 4 bytes for ID + 4 bytes for numlumps + 4 bytes for infotableofs
        const val DIRECTORY_ENTRY_SIZE = 16 // 4 bytes for filepos + 4 bytes for size + 8 bytes for name
    }

    // Data structures to be written to the WAD file
    private var identification: String = "PWAD" // Default to PWAD (Patch WAD)
    private val directoryEntries = mutableListOf<WadParser.DirectoryEntry>()
    
    /**
     * Write a WAD file using the provided data structures.
     */
    fun write(
        vertexes: List<Vertex>,
        lineDefs: List<LineDef>,
        sideDefs: List<SideDef>,
        sectors: List<Sector>,
        things: List<Thing>,
        nodes: List<Node>,
        subSectors: List<SubSector>,
        segs: List<Seg>
    ): Boolean {
        try {
            // Create a new file
            val file = File(outputPath)
            if (file.exists()) {
                file.delete()
            }
            
            RandomAccessFile(outputPath, "rw").use { raf ->
                // Reserve space for the header (will be written at the end)
                raf.seek(WAD_HEADER_SIZE.toLong())
                
                // Write map marker lump so engines recognize the level (e.g., MAP01)
                val mapMarkerPos = raf.filePointer.toInt()
                addDirectoryEntry("MAP01", mapMarkerPos, 0)

                // Write level lumps in canonical order expected by DOOM engines
                // Always write lumps, even if empty, so the map is recognized.

                // Ensure there is at least one Player 1 start (Thing type 1).
                // Some generation paths may produce no THINGS; GZDoom will refuse to start the map.
                val ensuredThings = if (things.any { it.type.toInt() == 1 }) {
                    things
                } else {
                    println("[WADWriter] No Player 1 start found; injecting a default one at (0,0).")
                    val injected = Thing(0, 0, 0, 1, 7)
                    things + injected
                }

                writeThings(raf, ensuredThings)
                writeLineDefs(raf, lineDefs)
                writeSideDefs(raf, sideDefs)
                writeVertexes(raf, vertexes)
                writeSegs(raf, segs)
                writeSubSectors(raf, subSectors)
                writeNodes(raf, nodes)
                writeSectors(raf, sectors)
                
                // Record the position of the directory
                val directoryOffset = raf.filePointer.toInt()
                
                // Write the directory
                writeDirectory(raf)
                
                // Go back and write the header
                raf.seek(0)
                writeHeader(raf, directoryOffset)
            }
            
            println("WAD file written successfully: $outputPath")
            return true
        } catch (e: Exception) {
            println("Error writing WAD file: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Write the WAD file header.
     */
    private fun writeHeader(raf: RandomAccessFile, directoryOffset: Int) {
        val buffer = ByteBuffer.allocate(WAD_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        
        // Write identification (IWAD or PWAD)
        buffer.put(identification.toByteArray(StandardCharsets.US_ASCII))
        
        // Write number of lumps
        buffer.putInt(directoryEntries.size)
        
        // Write directory offset
        buffer.putInt(directoryOffset)
        
        // Write to file
        buffer.flip()
        raf.write(buffer.array())
    }
    
    /**
     * Write the directory entries.
     */
    private fun writeDirectory(raf: RandomAccessFile) {
        for (entry in directoryEntries) {
            val buffer = ByteBuffer.allocate(DIRECTORY_ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            
            // Write file position
            buffer.putInt(entry.filePos)
            
            // Write size
            buffer.putInt(entry.size)
            
            // Write name (padded to 8 bytes)
            val nameBytes = entry.name.toByteArray(StandardCharsets.US_ASCII)
            buffer.put(nameBytes)
            
            // Pad with zeros if name is less than 8 bytes
            for (i in nameBytes.size until 8) {
                buffer.put(0)
            }
            
            // Write to file
            buffer.flip()
            raf.write(buffer.array())
        }
    }
    
    /**
     * Add a directory entry.
     */
    private fun addDirectoryEntry(name: String, filePos: Int, size: Int) {
        directoryEntries.add(WadParser.DirectoryEntry(filePos, size, name))
    }
    
    /**
     * Write VERTEXES data.
     */
    private fun writeVertexes(raf: RandomAccessFile, vertexes: List<Vertex>) {
        val filePos = raf.filePointer.toInt()
        val vertexSize = 4 // 2 shorts (x, y)
        val totalSize = vertexes.size * vertexSize
        
        for (vertex in vertexes) {
            val buffer = ByteBuffer.allocate(vertexSize).order(ByteOrder.LITTLE_ENDIAN)
            
            // Write x and y coordinates
            buffer.putShort(vertex.x)
            buffer.putShort(vertex.y)
            
            // Write to file
            buffer.flip()
            raf.write(buffer.array())
        }
        
        // Add directory entry
        addDirectoryEntry("VERTEXES", filePos, totalSize)
    }
    
    /**
     * Write LINEDEFS data.
     */
    private fun writeLineDefs(raf: RandomAccessFile, lineDefs: List<LineDef>) {
        val filePos = raf.filePointer.toInt()
        val lineDefSize = 14 // 2 shorts + 5 shorts
        val totalSize = lineDefs.size * lineDefSize
        
        for (lineDef in lineDefs) {
            val buffer = ByteBuffer.allocate(lineDefSize).order(ByteOrder.LITTLE_ENDIAN)
            
            // Write start and end vertex indices
            buffer.putShort(lineDef.startVertex.toShort())
            buffer.putShort(lineDef.endVertex.toShort())
            
            // Write flags, special type, and sector tag
            buffer.putShort(lineDef.flags)
            buffer.putShort(lineDef.specialType)
            buffer.putShort(lineDef.sectorTag)
            
            // Write sidedef indices
            buffer.putShort(lineDef.rightSideDef.toShort())
            buffer.putShort(lineDef.leftSideDef.toShort())
            
            // Write to file
            buffer.flip()
            raf.write(buffer.array())
        }
        
        // Add directory entry
        addDirectoryEntry("LINEDEFS", filePos, totalSize)
    }
    
    /**
     * Write SIDEDEFS data.
     */
    private fun writeSideDefs(raf: RandomAccessFile, sideDefs: List<SideDef>) {
        val filePos = raf.filePointer.toInt()
        val sideDefSize = 30 // 2 shorts + 3 * 8 chars + 1 short
        val totalSize = sideDefs.size * sideDefSize
        
        for (sideDef in sideDefs) {
            val buffer = ByteBuffer.allocate(sideDefSize).order(ByteOrder.LITTLE_ENDIAN)
            
            // Write x and y offsets
            buffer.putShort(sideDef.xOffset)
            buffer.putShort(sideDef.yOffset)
            
            // Write texture names (padded to 8 bytes each)
            writeFixedLengthString(buffer, sideDef.upperTexture, 8)
            writeFixedLengthString(buffer, sideDef.lowerTexture, 8)
            writeFixedLengthString(buffer, sideDef.middleTexture, 8)
            
            // Write sector index
            buffer.putShort(sideDef.sector.toShort())
            
            // Write to file
            buffer.flip()
            raf.write(buffer.array())
        }
        
        // Add directory entry
        addDirectoryEntry("SIDEDEFS", filePos, totalSize)
    }
    
    /**
     * Write SECTORS data.
     */
    private fun writeSectors(raf: RandomAccessFile, sectors: List<Sector>) {
        val filePos = raf.filePointer.toInt()
        val sectorSize = 26 // 2 shorts + 2 * 8 chars + 3 shorts
        val totalSize = sectors.size * sectorSize
        
        for (sector in sectors) {
            val buffer = ByteBuffer.allocate(sectorSize).order(ByteOrder.LITTLE_ENDIAN)
            
            // Write floor and ceiling heights
            buffer.putShort(sector.floorHeight)
            buffer.putShort(sector.ceilingHeight)
            
            // Write texture names (padded to 8 bytes each)
            writeFixedLengthString(buffer, sector.floorTexture, 8)
            writeFixedLengthString(buffer, sector.ceilingTexture, 8)
            
            // Write light level, special type, and tag
            buffer.putShort(sector.lightLevel)
            buffer.putShort(sector.specialType)
            buffer.putShort(sector.tag)
            
            // Write to file
            buffer.flip()
            raf.write(buffer.array())
        }
        
        // Add directory entry
        addDirectoryEntry("SECTORS", filePos, totalSize)
    }
    
    /**
     * Write THINGS data.
     */
    private fun writeThings(raf: RandomAccessFile, things: List<Thing>) {
        val filePos = raf.filePointer.toInt()
        val thingSize = 10 // 5 shorts
        val totalSize = things.size * thingSize
        
        for (thing in things) {
            val buffer = ByteBuffer.allocate(thingSize).order(ByteOrder.LITTLE_ENDIAN)
            
            // Write x and y positions
            buffer.putShort(thing.x)
            buffer.putShort(thing.y)
            
            // Write angle, type, and flags
            buffer.putShort(thing.angle)
            buffer.putShort(thing.type)
            buffer.putShort(thing.flags)
            
            // Write to file
            buffer.flip()
            raf.write(buffer.array())
        }
        
        // Add directory entry
        addDirectoryEntry("THINGS", filePos, totalSize)
    }
    
    /**
     * Write NODES data.
     */
    private fun writeNodes(raf: RandomAccessFile, nodes: List<Node>) {
        val filePos = raf.filePointer.toInt()
        val nodeSize = 28 // 12 shorts + 2 shorts as ints
        val totalSize = nodes.size * nodeSize
        
        for (node in nodes) {
            val buffer = ByteBuffer.allocate(nodeSize).order(ByteOrder.LITTLE_ENDIAN)
            
            // Write partition line
            buffer.putShort(node.xPartition)
            buffer.putShort(node.yPartition)
            buffer.putShort(node.xChange)
            buffer.putShort(node.yChange)
            
            // Write right bounding box
            buffer.putShort(node.rightBoxTop)
            buffer.putShort(node.rightBoxBottom)
            buffer.putShort(node.rightBoxLeft)
            buffer.putShort(node.rightBoxRight)
            
            // Write left bounding box
            buffer.putShort(node.leftBoxTop)
            buffer.putShort(node.leftBoxBottom)
            buffer.putShort(node.leftBoxLeft)
            buffer.putShort(node.leftBoxRight)
            
            // Write child nodes
            buffer.putShort(node.rightChild.toShort())
            buffer.putShort(node.leftChild.toShort())
            
            // Write to file
            buffer.flip()
            raf.write(buffer.array())
        }
        
        // Add directory entry
        addDirectoryEntry("NODES", filePos, totalSize)
    }
    
    /**
     * Write SSECTORS data.
     */
    private fun writeSubSectors(raf: RandomAccessFile, subSectors: List<SubSector>) {
        val filePos = raf.filePointer.toInt()
        val subSectorSize = 4 // 1 short + 1 short as int
        val totalSize = subSectors.size * subSectorSize
        
        for (subSector in subSectors) {
            val buffer = ByteBuffer.allocate(subSectorSize).order(ByteOrder.LITTLE_ENDIAN)
            
            // Write segment count and first segment index
            buffer.putShort(subSector.segCount)
            buffer.putShort(subSector.firstSeg.toShort())
            
            // Write to file
            buffer.flip()
            raf.write(buffer.array())
        }
        
        // Add directory entry
        addDirectoryEntry("SSECTORS", filePos, totalSize)
    }
    
    /**
     * Write SEGS data.
     */
    private fun writeSegs(raf: RandomAccessFile, segs: List<Seg>) {
        val filePos = raf.filePointer.toInt()
        val segSize = 12 // 2 shorts as ints + 4 shorts
        val totalSize = segs.size * segSize
        
        for (seg in segs) {
            val buffer = ByteBuffer.allocate(segSize).order(ByteOrder.LITTLE_ENDIAN)
            
            // Write start and end vertex indices
            buffer.putShort(seg.startVertex.toShort())
            buffer.putShort(seg.endVertex.toShort())
            
            // Write angle, linedef index, direction, and offset
            buffer.putShort(seg.angle)
            buffer.putShort(seg.lineDef.toShort())
            buffer.putShort(seg.direction)
            buffer.putShort(seg.offset)
            
            // Write to file
            buffer.flip()
            raf.write(buffer.array())
        }
        
        // Add directory entry
        addDirectoryEntry("SEGS", filePos, totalSize)
    }
    
    /**
     * Write a fixed-length string to a ByteBuffer.
     */
    private fun writeFixedLengthString(buffer: ByteBuffer, str: String, length: Int) {
        val bytes = str.toByteArray(StandardCharsets.US_ASCII)
        
        // Write the string bytes
        for (i in 0 until minOf(bytes.size, length)) {
            buffer.put(bytes[i])
        }
        
        // Pad with zeros if the string is shorter than the required length
        for (i in bytes.size until length) {
            buffer.put(0)
        }
    }
}