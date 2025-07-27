package wadtool

import java.io.File

/**
 * Main entry point for the WAD Tool application.
 * This tool parses DOOM/DOOM2 WAD files and displays information about their contents.
 */
fun main() {
    println("WAD Tool - DOOM/DOOM2 WAD File Parser")
    println("======================================")
    
    // Path to the WAD file
    val wadFilePath = "WADTool/data/ATTACK.WAD"
    
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