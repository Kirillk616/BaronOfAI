package wadtool

import java.io.File

/**
 * Command-line tool that reads a prompt from a text file and generates
 * a simple DOOM2 level (using WadModels) based on the processed prompt.
 *
 * Prompt processing is wired through a Koog-like interface. If the real
 * Koog AI library is on the classpath, you can replace the stub in
 * KoogPromptProcessor with a concrete implementation. The rest of the
 * generation pipeline remains the same.
 */
fun main(args: Array<String>) {
    // Determine prompt file path: default to project-root "prompt.txt" if no args provided.
    val promptPath = if (args.isNotEmpty()) args[0] else "prompt.txt"
    val promptFile = File(promptPath)

    if (!promptFile.exists()) {
        if (args.isEmpty()) {
            println("Error: Default prompt file 'prompt.txt' not found in current directory.")
            println("Usage: PromptLevelGenerator <prompt.txt> [output.wad]")
        } else {
            println("Error: Prompt file not found at ${promptFile.path}")
        }
        return
    }

    val outputPath = if (args.size > 1) args[1] else "WADTool/data/GENAI.WAD"

    val prompt = promptFile.readText().trim()
    if (prompt.isEmpty()) {
        println("Error: Prompt file is empty")
        return
    }

    println("Reading prompt from: ${promptFile.path}")
    println("Processing prompt with Koog...")

    val koog = KoogPromptProcessor()
    val spec = koog.process(prompt)

    println("Prompt processed into level spec: $spec")

    val level = LevelBuilder.fromSpec(spec)

    val writer = WadWriter(outputPath)
    val ok = writer.write(
        level.vertexes,
        level.lineDefs,
        level.sideDefs,
        level.sectors,
        level.things,
        level.nodes,
        level.subSectors,
        level.segs
    )

    if (ok) {
        println("Generated WAD at: $outputPath")
        // Also generate an SVG preview of the level geometry using the existing SvgGenerator.
        try {
            val parser = WadParser(outputPath)
            if (parser.parse()) {
                val svgPath = if (outputPath.endsWith(".WAD", ignoreCase = true))
                    outputPath.replace(Regex("(?i)\\.wad$"), ".svg")
                else
                    "WADTool/data/level.svg"
                val svgOk = parser.generateSvg(svgPath)
                if (!svgOk) {
                    println("Warning: SVG generation failed.")
                }
            } else {
                println("Warning: Could not parse generated WAD to produce SVG.")
            }
        } catch (e: Exception) {
            println("Warning: Exception during SVG generation: ${e.message}")
        }
    } else {
        println("Failed to generate WAD at: $outputPath")
    }
    
    
}

/**
 * Minimal spec returned by the prompt processor. This is intentionally simple
 * so the generator can always produce a valid trivial map while remaining
 * extensible for richer generation.
 */
data class LevelSpec(
    val roomSize: Int = 256,          // size of a square room in map units
    val floorHeight: Int = 0,
    val ceilingHeight: Int = 128,
    val floorTex: String = "FLOOR0_1",
    val ceilTex: String = "CEIL1_1",
    val wallTex: String = "STARTAN2",
    val light: Int = 160,             // 0-255
    val playerAngle: Int = 0          // degrees
)

/**
 * Koog prompt processor adapter. This is a placeholder that can be replaced
 * with an actual Koog AI client. For now it performs a tiny bit of parsing
 * from the prompt to influence size/lighting/textures, while serving as
 * the integration point named after Koog as requested.
 */
class KoogPromptProcessor {
    fun process(prompt: String): LevelSpec {
        // TODO: Replace this stub with actual Koog AI library invocation.
        // For example:
        // val client = KoogClient(apiKey)
        // val result = client.generateLevelSpec(prompt)
        // return result.toLevelSpec()

        // Heuristic stub: derive a few properties from keywords/numbers in the prompt.
        val lower = prompt.lowercase()
        val roomSize = Regex("""(^|\s)([1-9][0-9]{1,3})(\s|$)""")
            .find(lower)?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(128, 4096) ?: 384

        val light = when {
            listOf("dark", "dim", "gloom").any { it in lower } -> 96
            listOf("bright", "shiny", "sunny").any { it in lower } -> 224
            else -> 160
        }

        val wallTex = when {
            listOf("tech", "base", "computer").any { it in lower } -> "STARTAN2"
            listOf("hell", "flesh", "gore").any { it in lower } -> "SKINFACE"
            listOf("stone", "ruin", "castle").any { it in lower } -> "STONE2"
            else -> "STARTAN2"
        }

        val floorTex = when {
            listOf("lava", "fire").any { it in lower } -> "LAVA1"
            listOf("water", "pool").any { it in lower } -> "FWATER1"
            else -> "FLOOR0_1"
        }

        val ceilTex = when {
            listOf("sky", "outdoor").any { it in lower } -> "F_SKY1"
            else -> "CEIL1_1"
        }

        val angle = when {
            "north" in lower -> 0
            "east" in lower -> 90
            "south" in lower -> 180
            "west" in lower -> 270
            else -> 0
        }

        return LevelSpec(
            roomSize = roomSize,
            floorHeight = 0,
            ceilingHeight = 128,
            floorTex = floorTex,
            ceilTex = ceilTex,
            wallTex = wallTex,
            light = light,
            playerAngle = angle
        )
    }
}

/**
 * Constructs a trivial, valid set of WadModels lists from LevelSpec.
 * Creates a single square sector with four walls and a player start.
 */
object LevelBuilder {
    data class BuiltLevel(
        val vertexes: List<Vertex>,
        val lineDefs: List<LineDef>,
        val sideDefs: List<SideDef>,
        val sectors: List<Sector>,
        val things: List<Thing>,
        val nodes: List<Node>,
        val subSectors: List<SubSector>,
        val segs: List<Seg>
    )

    fun fromSpec(spec: LevelSpec): BuiltLevel {
        val half = (spec.roomSize / 2).coerceAtLeast(64)
        // Define a square centered at (0,0)
        val v0 = Vertex((-half).toShort(), (-half).toShort())
        val v1 = Vertex((half).toShort(), (-half).toShort())
        val v2 = Vertex((half).toShort(), (half).toShort())
        val v3 = Vertex((-half).toShort(), (half).toShort())
        val vertexes = listOf(v0, v1, v2, v3)

        val sector = Sector(
            floorHeight = spec.floorHeight.toShort(),
            ceilingHeight = spec.ceilingHeight.toShort(),
            floorTexture = spec.floorTex.take(8),
            ceilingTexture = spec.ceilTex.take(8),
            lightLevel = spec.light.toShort(),
            specialType = 0,
            tag = 0
        )
        val sectors = listOf(sector)

        // For a simple box, four sidedefs facing the single sector.
        val sideDefs = listOf(
            SideDef(0, 0, "-", "-", spec.wallTex.take(8), 0), // south wall
            SideDef(0, 0, "-", "-", spec.wallTex.take(8), 0), // east wall
            SideDef(0, 0, "-", "-", spec.wallTex.take(8), 0), // north wall
            SideDef(0, 0, "-", "-", spec.wallTex.take(8), 0)  // west wall
        )

        // Four linedefs forming a loop: v0->v1->v2->v3->v0
        val lineDefs = listOf(
            LineDef(0, 1, flags = 0, specialType = 0, sectorTag = 0, rightSideDef = 0, leftSideDef = -1),
            LineDef(1, 2, flags = 0, specialType = 0, sectorTag = 0, rightSideDef = 1, leftSideDef = -1),
            LineDef(2, 3, flags = 0, specialType = 0, sectorTag = 0, rightSideDef = 2, leftSideDef = -1),
            LineDef(3, 0, flags = 0, specialType = 0, sectorTag = 0, rightSideDef = 3, leftSideDef = -1)
        )

        // Player 1 start is type 1 in DOOM II; flags 7 for all skills and single-player
        val player = Thing(0, 0, spec.playerAngle.toShort(), type = 1, flags = 7)
        val things = listOf(player)

        // Keep advanced BSP data empty for now. Source ports typically rebuild or ignore.
        val nodes = emptyList<Node>()
        val subSectors = emptyList<SubSector>()
        val segs = emptyList<Seg>()

        return BuiltLevel(vertexes, lineDefs, sideDefs, sectors, things, nodes, subSectors, segs)
    }
}
