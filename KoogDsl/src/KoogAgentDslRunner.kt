package wadtool

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking

fun levelGenerationAgent(apiKey: String): AIAgent<String, LevelBuilder.BuiltLevel> {

    val strategy: AIAgentStrategy<String, LevelBuilder.BuiltLevel> = strategy("doom-level-spec-agent") {
        val specNode by nodeLLMRequestStructured<LevelBuilder.BuiltLevel>(
            name = "Generate LevelSpec JSON",
            //examples = listOf(listOf(DslAiSpec()))
        )
        edge(nodeStart forwardTo specNode)
        edge(
            specNode forwardTo nodeFinish
                    onCondition { it.isSuccess }
                    transformed { it.getOrThrow().structure }
        )
    }

    val agentConfig = AIAgentConfig(
        prompt = prompt(id = "doom-level-spec-agent") {
            system(
        "You are a level spec generator for a DOOM II map. " +
                "Produce a strict JSON object. No extra text. " +
                "Do not make levels too hard (100 cyberdemons, barely any medikits, etc) " +
                "Do not make levels too easy either (invulnerability, megasphere and BFG9000s everywhere) " +
                "Keep at least some realism, don't make nonsensical poorly designed levels " +
                "Also keep gameplay in the shooter mindset, don't make maps like the infamous Habitat from TNT, keep toxic tunnels and nukage platforms to an absolute minimum " +
                "Do not put monsters in inaccessible locations. Keep 100% kills possible " +
                "Do not tag sectors as secrets if they have obstacles in the way, don't pull an E4M3, E4M7 or Industrial Zone. Again, MAX should be possible. " +
                "Keep giant rooms to a minimum. If hiding some items or weapons, don't hide them in the middle of nowhere, like the armor bonus in E1M7. " +
                "ALWAYS change floor height when making a damaging floor (nukage, lava, blood, etc), and make sure the floor height change makes the hazard's sector have a height SMALLER than the bordering sectors, unless it's a river or fall. " +
                "Always keep enough fighting space. " +
                "Don't make ammo scarce. Infighting of monsters should be optional , not required. " +
                "Always keep a proportion of medikits/stimpacks to the map size. " +
                "Keep super-powerful enemies to a minimum. Super shotgun should work on most enemies in the level " +
                "Do not make up slop. Use common sense in most well-designed Official Doom maps, especially John Romeros. " +
                "Keep optional keys to a minimum. There should be at least some keys that are required to exit the map. " +
                "To prevent visplane overflows in the original DOS executable, do NOT put too many sectors in one \"beautiful\" view. " +
                "Also, do not put too many lifts/platforms moving at once, which will prevent another error in the original DOS Doom, \"no more plats\" error. Make sure crushers only start when approaching them, and stop when they are out of sight. " +
                "Keep damaging floors that are inescapable to an absolute minimum. There should always be at least SOME way to escape an area without softlocking yourself. " +
                "Use only these available textures: " +
                "Brick and masonry: BRICK1, BRICK2, BRICK3, BRICK4, BRICK5, BRNBIGC, BRNBIGL, BRNBIGR, BROWN1, BROWN96, BROWNGRN, BROWNHUG, CRATELIT, GRAYTALL, ZIMMER1. " +
                "Metallic and tech: BIGDOOR1, BIGDOOR2, COMPBLUE, COMPTALL, COMPWERD, DOORBLU, DOORRED, DOORYEL, GRAYBIG, GRAYMET, METAL1, METAL2, MIDGRATE, MIDBARS1, MIDBARS3, SILVER1, SILVER2, SILVER3, SKINMET1, SKINMET2, STARTAN2, STARTAN3, TEKWALL1, TEKWALL4. " +
                "Organic and hellish: GRNROCK, MARBLE1, MARBLE2, MARBLE3, MARBGRAY, SP_ROCK1, REDWALL, ROCKRED1, SKIN2, SKINFACE, SKSNAKE1, SKSNAKE2, SKULWAL3, SKULWALL. " +
                "Stone: STONE, STONE2, STONE3, SP_ROCK1, STONGARG, STUCCO, STUCCO1, STUCCO2, STUCCO3. " +
                "Miscellaneous: BROWN144, BROWNPIP, CEMENT1, CEMENT2, CEMENT3, CEMENT4, CEMENT5, CEMENT6, CEMENT7, CEMENT8, CEMENT9, CRACKLE2, CRACKLE4, CRATE1, CRATE2, CRATE3, EXITDOOR, WOOD1, WOOD3, WOOD4, WOOD5, WOODGARG, WOODMET1, WOODMET2, WOODMET3, WOODMET4, WOODVERT. " +
                "Animated textures: FIREBLU1, FIREMAG1, FIRELAV3, NUKEPOIS, SLADRIP1, SLADRIP2, SLADRIP3. " +
                "Difficulty should always be fair. Do not put hitscan (troopers, sergeants, chaingun dudes) enemies in far away areas where you cannot see them (cough cough, TNT Map27 Mount Pain) " +
                "In general, always place hitscan enemies carefully. Projectile-firing enemies are easier to dodge for pro players.")
        },
        model = OpenAIModels.Chat.GPT4_1,
        maxAgentIterations = 50
    )

    return AIAgent<String, LevelBuilder.BuiltLevel>(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        strategy = strategy,
        agentConfig = agentConfig,
    )
}

fun main(): Unit = runBlocking {
    try {
        val message = "Walmart like building, with one huge room A with 6 isles of shelves and 3 smaller rooms B,C,D connected with " +
                "room A by doors. Door from A to B locked with blue key. Door from A to C locked with red key. Door from A to D locked with yellow key." +
                "Set room light level to 160 (bright indoor lighting). Put the player 1 inside in the center of the room." +
                "put 4 imps in the corners of the room, make sure that they do not touch the walls."
        val apiKey = System.getenv("OPENAI_API_KEY")
        if (apiKey.isNullOrBlank()) {
            println("OPENAI_API_KEY is not set. Skipping agent execution. Set the environment variable to run this sample.")
            return@runBlocking
        }
        val built: LevelBuilder.BuiltLevel = levelGenerationAgent(apiKey).run(message)

        val outWad = "WADTool\\data\\GENAI.WAD"
        val writer = WadWriter(outWad)
        val wrote = writer.write(
            built.vertexes,
            built.lineDefs,
            built.sideDefs,
            built.sectors,
            built.things,
            built.nodes,
            built.subSectors,
            built.segs
        )
        if (!wrote) {
            println("Failed to write WAD to $outWad")
            return@runBlocking
        }
        println("WAD written to $outWad")

        // Save level definition as JSON
        val jsonFile = java.io.File("WADTool\\data\\level_definition.json")
        jsonFile.parentFile.mkdirs()
        val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(built)
        jsonFile.writeText(json)
        println("Level definition saved to ${jsonFile.absolutePath}")

        val parser = WadParser(outWad)
        if (parser.parse()) {
            val svgOut = "WADTool\\data\\GENAI.svg"
            val ok = parser.generateSvg(svgOut)
            if (!ok) println("Failed to generate SVG at $svgOut")
        } else {
            println("Parsing generated WAD failed; cannot render SVG")
        }

    } catch (t: Throwable) {
        // Do not fail the Gradle process with a non-zero exit; print a helpful message instead.
        System.err.println("Doom gen Execution failed: ${t.message}")
        t.printStackTrace()
        // Intentionally swallow the exception to avoid non-zero exit code in Gradle runs of this sample.
    }
}


