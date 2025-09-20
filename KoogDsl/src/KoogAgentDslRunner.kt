package wadtool

import ai.koog.agents.AIAgent
import ai.koog.agents.config.AIAgentConfig
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.nodes.nodeLLMRequestStructured
import ai.koog.agents.prompt.prompt
import ai.koog.agents.strategy.AIAgentStrategy
import ai.koog.agents.strategy.edge
import ai.koog.agents.strategy.forwardTo
import ai.koog.agents.strategy.nodeFinish
import ai.koog.agents.strategy.nodeStart
import ai.koog.agents.strategy.onCondition
import ai.koog.agents.strategy.strategy
import ai.koog.agents.strategy.transformed
import ai.koog.data.JsonSchemaGenerator
import ai.koog.data.JsonStructuredData
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.providers.openai.OpenAIModels
import ai.koog.providers.openai.simpleOpenAIExecutor
import ai.koog.tools.toolRegistry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking

data class DslAiSpec(
    val roomSize: Int = 384,
    val floorHeight: Int = 0,
    val ceilingHeight: Int = 128,
    val floorTex: String = "FLOOR0_1",
    val ceilTex: String = "CEIL1_1",
    val wallTex: String = "STARTAN2",
    val light: Int = 160,
    val playerAngle: Int = 0
)

fun levelGenerationAgent(userPrompt: String): AIAgent<String, LevelBuilder.BuiltLevel> {

    val strategy: AIAgentStrategy<String, LevelBuilder.BuiltLevel> = strategy("doom-level-spec-agent") {
        val specNode by nodeLLMRequestStructured<DslAiSpec>(
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
                "You are a level spec generator for a DOOM-like map. " +
                        "Produce a strict JSON object: roomSize(int), floorHeight(int), ceilingHeight(int), " +
                        "floorTex(string), ceilTex(string), wallTex(string), light(int 0-255), playerAngle(int degrees). " +
                        "No extra text."
            )
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 50
    )

    val agent = AIAgent<String, DslAiSpec>(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
    )

    val msg = "Create a level spec based on this prompt: \"$promptText\""
}

fun main(): Unit = runBlocking {
    try {
        val message = "Square room with red walls"
        val apiKey = System.getenv("OPENAI_API_KEY")
        if (apiKey.isNullOrBlank()) {
            println("OPENAI_API_KEY is not set. Skipping agent execution. Set the environment variable to run this sample.")
            return@runBlocking
        }
        val result = businessClarificationAgent(apiKey).run(message)
        println(result)
    } catch (t: Throwable) {
        // Do not fail the Gradle process with a non-zero exit; print a helpful message instead.
        System.err.println("[NAICS Agent] Execution failed: ${t.message}")
        t.printStackTrace()
        // Intentionally swallow the exception to avoid non-zero exit code in Gradle runs of this sample.
    }
}


