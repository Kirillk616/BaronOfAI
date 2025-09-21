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
                        "Produce a strict JSON object. No extra text."
            )
        },
        model = OpenAIModels.Chat.GPT5,
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
        val message = "Walmart like building, with one huge room with lot of shelves and 3 smaller rooms connected by doors. " +
                "Set room light level to 160 (bright indoor lighting). Put the player 1 inside in the center of the room." +
                "put 4 imps in the corners of the room"
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


