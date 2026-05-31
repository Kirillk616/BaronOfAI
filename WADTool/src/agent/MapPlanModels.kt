package wadtool.agent

import kotlinx.serialization.Serializable

@Serializable
data class MapPromptProfile(
    val originalPrompt: String,
    val title: String,
    val theme: String,
    val difficulty: String,
    val targetRoomCount: Int,
    val requestedKeys: List<String>,
    val requestedSetPieces: List<String>,
    val lightingHint: String
)

@Serializable
data class MapPlan(
    val profile: MapPromptProfile,
    val topology: List<RoomNode>,
    val connections: List<ConnectionPlan>,
    val rooms: List<RoomPlan> = emptyList(),
    val validation: ValidationReport = ValidationReport()
)

@Serializable
data class RoomNode(
    val id: String,
    val label: String,
    val role: String,
    val required: Boolean,
    val lock: String? = null,
    val grantsKey: String? = null,
    val notes: String = ""
)

@Serializable
data class ConnectionPlan(
    val from: String,
    val to: String,
    val kind: String,
    val requiredKey: String? = null,
    val notes: String = ""
)

@Serializable
data class RoomPlan(
    val id: String,
    val shape: String,
    val width: Int,
    val height: Int,
    val floorHeight: Int,
    val ceilingHeight: Int,
    val light: Int,
    val floorTexture: String,
    val ceilingTexture: String,
    val wallTexture: String,
    val encounterHint: String,
    val traversalHint: String,
    val landmark: String
)

@Serializable
data class ValidationReport(
    val passed: Boolean = false,
    val score: Int = 0,
    val hardIssues: List<String> = emptyList(),
    val softIssues: List<String> = emptyList()
)

interface MapSubagent<I, O> {
    val name: String
    fun run(input: I): O
}

