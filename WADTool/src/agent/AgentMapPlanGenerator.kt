package wadtool.agent

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import wadtool.WadParser
import wadtool.WadWriter
import wadtool.generateSvg
import wadtool.storage.FileLevelStorage

fun main(args: Array<String>) {
    val promptPath = args.getOrNull(0) ?: "prompt.txt"
    val outputPath = args.getOrNull(1) ?: "WADTool/data/map_plan.json"
    val wadPath = args.getOrNull(2) ?: "WADTool/data/AGENT_GEN.WAD"
    val promptFile = File(promptPath)
    if (!promptFile.exists()) {
        println("Error: prompt file not found at ${promptFile.path}")
        println("Usage: AgentMapPlanGenerator <prompt.txt> [output-map-plan.json] [output.wad]")
        return
    }

    val prompt = promptFile.readText().trim()
    if (prompt.isEmpty()) {
        println("Error: prompt file is empty")
        return
    }

    val plan = RootMapOrchestrator().generate(prompt)
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    val outputFile = File(outputPath)
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(json.encodeToString(plan))

    val level = MapPlanCompiler().compile(plan)
    val writer = WadWriter(wadPath)
    val wroteWad = writer.write(
        level.vertexes,
        level.lineDefs,
        level.sideDefs,
        level.sectors,
        level.things,
        level.nodes,
        level.subSectors,
        level.segs
    )

    println("Map plan written to: ${outputFile.path}")
    println("Title: ${plan.profile.title}")
    println("Theme: ${plan.profile.theme}, difficulty: ${plan.profile.difficulty}")
    println("Rooms: ${plan.topology.size}, connections: ${plan.connections.size}")
    println("Validation: ${if (plan.validation.passed) "passed" else "failed"} (${plan.validation.score})")
    if (wroteWad) {
        println("WAD written to: $wadPath")
        val parser = WadParser(wadPath)
        if (parser.parse()) {
            val svgPath = wadPath.replace(Regex("(?i)\\.wad$"), ".svg")
            if (parser.generateSvg(svgPath, level.annotations)) {
                val id = FileLevelStorage().save(prompt, File(wadPath), File(svgPath).readText())
                println("Saved level to storage with id: $id")
                println("Browser play URL: /levels/$id/play")
            }
        } else {
            println("Warning: generated WAD could not be parsed back for SVG rendering.")
        }
    } else {
        println("Warning: failed to write WAD to $wadPath")
    }
    if (plan.validation.hardIssues.isNotEmpty()) {
        println("Hard issues:")
        plan.validation.hardIssues.forEach { println("- $it") }
    }
    if (plan.validation.softIssues.isNotEmpty()) {
        println("Soft issues:")
        plan.validation.softIssues.forEach { println("- $it") }
    }
}
