package wadtool

import java.io.File

/**
 * Main entry point for the WAD Tool application.
 * This tool parses DOOM/DOOM2 WAD files and displays information about their contents.
 * It can also generate a new WAD file from the parsed data for verification.
 */
fun main() {
    println("WAD Tool - DOOM/DOOM2 WAD File Parser and Writer")
    println("================================================")
    
    // Path to the WAD file
    val wadFilePath = "WADTool/data/ATTACK.WAD"
    val generatedWadFilePath = "WADTool/data/ATTACK_GEN.WAD"
    
    // Check if the file exists
    val wadFile = File(wadFilePath)
    if (!wadFile.exists()) {
        println("Error: WAD file not found at $wadFilePath")
        return
    }
    
    println("Parsing WAD file: $wadFilePath")
    
    // Create a parser and parse the WAD file
    val parser = WadParser(wadFilePath)
    val success = parser.parse()
    
    if (success) {
        // Display a summary of the parsed data
        println("\nParsing completed successfully!")
        println(parser.getSummary())
        
        // Display some sample data if available
        displaySampleData(parser)
        
        // Generate SVG file from the parsed data
        println("\nGenerating SVG file...")
        val svgSuccess = parser.generateSvg()
        if (svgSuccess) {
            println("SVG generation completed successfully!")
        } else {
            println("Failed to generate SVG file.")
        }
        
        // Generate a new WAD file from the parsed data
        println("\nGenerating new WAD file...")
        val writer = WadWriter(generatedWadFilePath)
        val writeSuccess = writer.write(
            parser.vertexes,
            parser.lineDefs,
            parser.sideDefs,
            parser.sectors,
            parser.things,
            parser.nodes,
            parser.subSectors,
            parser.segs
        )
        
        if (writeSuccess) {
            println("WAD file generation completed successfully: $generatedWadFilePath")
            
            // Verify the generated WAD file by parsing it
            println("\nVerifying generated WAD file...")
            val verifyParser = WadParser(generatedWadFilePath)
            val verifySuccess = verifyParser.parse()
            
            if (verifySuccess) {
                println("Generated WAD file parsed successfully!")
                println(verifyParser.getSummary())
                
                // Compare the original and generated WAD files
                println("\nComparing original and generated WAD files...")
                compareWadFiles(parser, verifyParser)
            } else {
                println("Failed to parse the generated WAD file.")
            }
        } else {
            println("Failed to generate WAD file.")
        }
    } else {
        println("\nFailed to parse the WAD file.")
    }
}

/**
 * Display sample data from the parsed WAD file.
 */
private fun displaySampleData(parser: WadParser) {
    println("\nSample Data:")
    println("------------")
    
    // Display some vertexes if available
    if (parser.vertexes.isNotEmpty()) {
        println("\nVertexes (first 5):")
        parser.vertexes.take(5).forEachIndexed { index, vertex ->
            println("  $index: $vertex")
        }
    }
    
    // Display some linedefs if available
    if (parser.lineDefs.isNotEmpty()) {
        println("\nLineDefs (first 5):")
        parser.lineDefs.take(5).forEachIndexed { index, lineDef ->
            println("  $index: $lineDef")
        }
    }
    
    // Display some things if available
    if (parser.things.isNotEmpty()) {
        println("\nThings (first 5):")
        parser.things.take(5).forEachIndexed { index, thing ->
            println("  $index: $thing")
        }
    }
    
    // Display some sectors if available
    if (parser.sectors.isNotEmpty()) {
        println("\nSectors (first 5):")
        parser.sectors.take(5).forEachIndexed { index, sector ->
            println("  $index: $sector")
        }
    }
}

/**
 * Compare the data structures from two WAD files.
 * This function checks if the number of elements in each data structure is the same
 * and reports any differences.
 */
private fun compareWadFiles(original: WadParser, generated: WadParser) {
    var allMatch = true
    
    // Compare vertexes
    if (original.vertexes.size != generated.vertexes.size) {
        println("Vertex count mismatch: Original=${original.vertexes.size}, Generated=${generated.vertexes.size}")
        allMatch = false
    }
    
    // Compare linedefs
    if (original.lineDefs.size != generated.lineDefs.size) {
        println("LineDef count mismatch: Original=${original.lineDefs.size}, Generated=${generated.lineDefs.size}")
        allMatch = false
    }
    
    // Compare sidedefs
    if (original.sideDefs.size != generated.sideDefs.size) {
        println("SideDef count mismatch: Original=${original.sideDefs.size}, Generated=${generated.sideDefs.size}")
        allMatch = false
    }
    
    // Compare sectors
    if (original.sectors.size != generated.sectors.size) {
        println("Sector count mismatch: Original=${original.sectors.size}, Generated=${generated.sectors.size}")
        allMatch = false
    }
    
    // Compare things
    if (original.things.size != generated.things.size) {
        println("Thing count mismatch: Original=${original.things.size}, Generated=${generated.things.size}")
        allMatch = false
    }
    
    // Compare nodes
    if (original.nodes.size != generated.nodes.size) {
        println("Node count mismatch: Original=${original.nodes.size}, Generated=${generated.nodes.size}")
        allMatch = false
    }
    
    // Compare subsectors
    if (original.subSectors.size != generated.subSectors.size) {
        println("SubSector count mismatch: Original=${original.subSectors.size}, Generated=${generated.subSectors.size}")
        allMatch = false
    }
    
    // Compare segs
    if (original.segs.size != generated.segs.size) {
        println("Seg count mismatch: Original=${original.segs.size}, Generated=${generated.segs.size}")
        allMatch = false
    }
    
    if (allMatch) {
        println("All data structures match in size between original and generated WAD files!")
        println("WAD file generation and parsing verification completed successfully.")
    } else {
        println("There are differences between the original and generated WAD files.")
        println("Please check the implementation for potential issues.")
    }
}