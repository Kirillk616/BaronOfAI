package wadtool

import java.io.File

/**
 * Generate an SVG file representing the level geometry for a parsed WAD.
 * This is implemented as an extension function on WadParser to keep
 * responsibilities separated from parsing logic while preserving the same call site.
 *
 * @param outputPath The path where the SVG file should be saved. If null, saves to "WADTool/data/level.svg"
 * @return True if the SVG was generated successfully, false otherwise
 */
fun WadParser.generateSvg(outputPath: String? = null): Boolean {
    if (vertexes.isEmpty() || lineDefs.isEmpty()) {
        println("Cannot generate SVG: No vertices or linedefs found")
        return false
    }

    return try {
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
        svgBuilder.append(
            """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <svg width="$svgWidth" height="$svgHeight" xmlns="http://www.w3.org/2000/svg">
                  <rect width="100%" height="100%" fill="black"/>
                  <g transform="translate(${svgWidth/2}, ${svgHeight/2}) scale($scale) translate(${-(minX + maxX)/2}, ${-(minY + maxY)/2})">
            """.trimIndent()
        )

        // Add lines for each linedef
        lineDefs.forEach { lineDef ->
            if (lineDef.startVertex < vertexes.size && lineDef.endVertex < vertexes.size) {
                val startVertex = vertexes[lineDef.startVertex]
                val endVertex = vertexes[lineDef.endVertex]

                svgBuilder.append(
                    """
                        <line x1="${startVertex.x}" y1="${-startVertex.y}" x2="${endVertex.x}" y2="${-endVertex.y}" stroke="#00FF00" stroke-width="${2/scale}"/>
                    """.trimIndent()
                )
            }
        }

        // Add player start positions (things)
        things.forEach { thing ->
            // Player start is type 1
            if (thing.type.toInt() == 1) {
                svgBuilder.append(
                    """
                        <circle cx="${thing.x}" cy="${-thing.y}" r="${8/scale}" fill="blue"/>
                        <line 
                            x1="${thing.x}" 
                            y1="${-thing.y}" 
                            x2="${thing.x + 20 * Math.cos(Math.toRadians(thing.angle.toDouble()))}" 
                            y2="${-thing.y - 20 * Math.sin(Math.toRadians(thing.angle.toDouble()))}" 
                            stroke="blue" 
                            stroke-width="${2/scale}"/>
                    """.trimIndent()
                )
            }
        }

        // Close SVG
        svgBuilder.append(
            """
                  </g>
                </svg>
            """.trimIndent()
        )

        // Write to file
        val finalOutputPath = outputPath ?: "WADTool/data/level.svg"
        File(finalOutputPath).writeText(svgBuilder.toString())

        println("SVG generated successfully: $finalOutputPath")
        true
    } catch (e: Exception) {
        println("Error generating SVG: ${e.message}")
        e.printStackTrace()
        false
    }
}
