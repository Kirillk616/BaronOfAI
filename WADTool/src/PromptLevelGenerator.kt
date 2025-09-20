package wadtool

import java.io.File
import kotlinx.serialization.Serializable

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
    private val apiKey: String? = System.getenv("OPENAI_API_KEY")
    private val model: String = System.getenv("KOOG_MODEL") ?: "gpt-4o-mini"

    /**
     * Calls Koog agent connected to OpenAI to transform a natural-language prompt
     * into a LevelSpec. Falls back to the previous heuristic if no API key is present
     * or if the call fails for any reason.
     */
    fun process(prompt: String): LevelSpec {
        // If Koog/OpenAI not configured, use fallback heuristic for offline usage.
        if (apiKey.isNullOrBlank()) {
            return heuristic(prompt)
        }
        return try {
            // Use Koog Agents with OpenAI provider when available (Gradle build includes Koog DSL).
            // We avoid complex project-wide wiring and do a minimal inline agent call here.
            val koogResult = runKoogAgent(prompt)
            // Validate and clamp values to safe ranges
            LevelSpec(
                roomSize = koogResult.roomSize.coerceIn(128, 4096),
                floorHeight = koogResult.floorHeight.coerceIn(-4096, 4096),
                ceilingHeight = koogResult.ceilingHeight.coerceIn(0, 8192).let { if (it <= koogResult.floorHeight) koogResult.floorHeight + 64 else it },
                floorTex = koogResult.floorTex.ifBlank { "FLOOR0_1" },
                ceilTex = koogResult.ceilTex.ifBlank { "CEIL1_1" },
                wallTex = koogResult.wallTex.ifBlank { "STARTAN2" },
                light = koogResult.light.coerceIn(0, 255),
                playerAngle = ((koogResult.playerAngle % 360) + 360) % 360
            )
        } catch (t: Throwable) {
            println("Warning: Koog/OpenAI call failed: ${t.message}. Falling back to heuristic.")
            heuristic(prompt)
        }
    }

    // Minimal Koog data holder matching LevelSpec fields
    private data class AiSpec(
        val roomSize: Int = 384,
        val floorHeight: Int = 0,
        val ceilingHeight: Int = 128,
        val floorTex: String = "FLOOR0_1",
        val ceilTex: String = "CEIL1_1",
        val wallTex: String = "STARTAN2",
        val light: Int = 160,
        val playerAngle: Int = 0
    )

    // Executes a Koog agent; tries Koog DSL runner via reflection (Gradle path). Falls back to direct HTTP.
    private fun runKoogAgent(prompt: String): AiSpec {
        // Try to use the DSL-based runner if available on the classpath (when built with Gradle Koog DSL sources)
        try {
            val runnerClazz = Class.forName("wadtool.KoogAgentDslRunner")
            val runMethod = runnerClazz.getMethod("run", String::class.java, String::class.java, String::class.java)
            val json = runMethod.invoke(null, prompt, apiKey, model) as String
            return parseJsonToAiSpec(json)
        } catch (_: Throwable) {
            // ignore and fallback to HTTP below
        }

        // Koog DSL not available; fallback to direct OpenAI HTTP call with instruction
        val system = """
            You are a level spec generator for a DOOM-like map. You must output a strict JSON object
            with fields: roomSize (int), floorHeight (int), ceilingHeight (int), floorTex (string),
            ceilTex (string), wallTex (string), light (int 0-255), playerAngle (int degrees).
            Do not include backticks or any text outside the JSON.
        """.trimIndent()
        return openAiHttpCall(system, prompt)
    }

    // Very small JSON parser tailored for our fields to avoid adding heavy deps beyond Jackson which is already present
    private fun parseJsonToAiSpec(json: String): AiSpec {
        try {
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            return mapper.readValue(json, AiSpec::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON from Koog/OpenAI: ${e.message}")
        }
    }

    // Direct HTTP call to OpenAI Chat Completions as a fallback path
    private fun openAiHttpCall(system: String, prompt: String): AiSpec {
        val uri = java.net.URI.create("https://api.openai.com/v1/chat/completions")
        val bodyJson = """
            {"model":"$model","messages":[
              {"role":"system","content":${jsonString(system)}},
              {"role":"user","content":${jsonString("Create a level spec based on this prompt: \"$prompt\". Return ONLY the JSON.")}}
            ],"temperature":0.2}
        """.trimIndent()
        val req = java.net.http.HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(bodyJson))
            .build()
        val client = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .build()
        val resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw IllegalStateException("OpenAI HTTP error ${resp.statusCode()}: ${resp.body()}")
        }
        // Extract the first message content
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val tree = mapper.readTree(resp.body())
        val content = tree.path("choices").firstOrNull()?.path("message")?.path("content")?.asText()
            ?: throw IllegalStateException("OpenAI response missing content")
        return parseJsonToAiSpec(content.trim())
    }

    private fun jsonString(text: String): String = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(text)

    // Previous heuristic retained as a fallback/offline mode
    private fun heuristic(prompt: String): LevelSpec {
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
    @Serializable
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
