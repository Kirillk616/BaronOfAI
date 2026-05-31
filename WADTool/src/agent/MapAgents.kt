package wadtool.agent

class PromptProfileAgent : MapSubagent<String, MapPromptProfile> {
    override val name: String = "prompt-profile-agent"

    override fun run(input: String): MapPromptProfile {
        val prompt = input.lineSequence()
            .map { it.trimEnd() }
            .filterNot { it.trimStart().startsWith("#") }
            .joinToString("\n")
            .trim()
        val lower = prompt.lowercase()
        val requestedKeys = listOf("blue", "red", "yellow").filter { key ->
            "$key key" in lower || "$key door" in lower
        }
        val setPieces = buildList {
            if (listOf("bridge", "catwalk").any { it in lower }) add("bridge")
            if (listOf("button", "switch").any { it in lower }) add("switch")
            if (listOf("lava", "nukage", "acid", "blood").any { it in lower }) add("hazard-floor")
            if (listOf("lift", "elevator", "platform").any { it in lower }) add("lift")
            if (listOf("warehouse", "walmart", "store", "shelf", "shelves").any { it in lower }) add("aisle-arena")
        }

        return MapPromptProfile(
            originalPrompt = prompt,
            title = inferTitle(lower),
            theme = inferTheme(lower),
            difficulty = inferDifficulty(lower),
            targetRoomCount = inferRoomCount(lower, requestedKeys),
            requestedKeys = requestedKeys.ifEmpty { listOf("blue") },
            requestedSetPieces = setPieces,
            lightingHint = when {
                listOf("dark", "dim", "gloom").any { it in lower } -> "dark"
                listOf("bright", "shiny", "office", "store").any { it in lower } -> "bright"
                else -> "balanced"
            }
        )
    }

    private fun inferTitle(lower: String): String = when {
        "walmart" in lower || "store" in lower -> "Retail Lockdown"
        "hell" in lower || "flesh" in lower -> "Furnace Prayer"
        "base" in lower || "tech" in lower -> "Relay Station"
        "castle" in lower || "stone" in lower -> "Broken Keep"
        else -> "Generated Doom Map"
    }

    private fun inferTheme(lower: String): String = when {
        listOf("walmart", "warehouse", "store", "shelf", "shelves").any { it in lower } -> "warehouse"
        listOf("hell", "flesh", "gore", "lava").any { it in lower } -> "hell"
        listOf("stone", "ruin", "castle").any { it in lower } -> "stone"
        listOf("tech", "base", "computer").any { it in lower } -> "techbase"
        else -> "techbase"
    }

    private fun inferDifficulty(lower: String): String = when {
        listOf("easy", "light", "casual").any { it in lower } -> "easy"
        listOf("hard", "brutal", "slaughter").any { it in lower } -> "hard"
        else -> "medium"
    }

    private fun inferRoomCount(lower: String, keys: List<String>): Int {
        val explicit = Regex("""\b([3-9]|1[0-2])\s+(rooms?|areas?)\b""")
            .find(lower)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (explicit != null) return explicit.coerceIn(3, 12)

        val base = 5 + keys.size
        val wantsSmall = Regex("""\b(small|compact|tiny)\b""").containsMatchIn(lower)
        val wantsLarge = Regex("""\b(large|huge|big|massive)\b""").containsMatchIn(lower)
        return when {
            wantsSmall -> base.coerceAtMost(6)
            wantsLarge -> (base + 2).coerceAtMost(12)
            else -> base.coerceIn(5, 9)
        }
    }
}

class TopologyAgent : MapSubagent<MapPromptProfile, MapPlan> {
    override val name: String = "topology-agent"

    override fun run(input: MapPromptProfile): MapPlan {
        val keys = input.requestedKeys
        val nodes = mutableListOf(
            RoomNode("start", "Start Room", "start", required = true, notes = "Safe orientation space with one clear exit."),
            RoomNode("foyer", "Entry Foyer", "connector", required = true, notes = "Introduces the visual theme before combat pressure."),
            RoomNode("hub", "Main Arena", "arena", required = true, notes = "Primary combat and navigation landmark.")
        )

        keys.forEachIndexed { index, key ->
            nodes += RoomNode(
                id = "${key}_key",
                label = "${key.capitalized()} Key Room",
                role = "key_room",
                required = true,
                grantsKey = key,
                notes = "Optional-looking branch that grants the $key key before the matching lock."
            )
            nodes += RoomNode(
                id = "${key}_lock",
                label = "${key.capitalized()} Locked Wing",
                role = "locked_room",
                required = true,
                lock = key,
                notes = "Required progression room gated by the $key key."
            )
            if (index == 0) {
                nodes += RoomNode(
                    id = "secret_cache",
                    label = "Secret Cache",
                    role = "secret",
                    required = false,
                    notes = "Small side reward that does not block map completion."
                )
            }
        }

        nodes += RoomNode("exit", "Exit Room", "exit", required = true, notes = "Distinct final space with readable exit line.")

        val connections = mutableListOf(
            ConnectionPlan("start", "foyer", "doorway"),
            ConnectionPlan("foyer", "hub", "doorway")
        )
        keys.forEach { key ->
            connections += ConnectionPlan("hub", "${key}_key", "doorway", notes = "Reachable before the $key lock.")
            connections += ConnectionPlan("hub", "${key}_lock", "locked_door", requiredKey = key)
        }
        if (nodes.any { it.id == "secret_cache" }) {
            connections += ConnectionPlan("foyer", "secret_cache", "secret_door", notes = "Optional early reward.")
        }
        connections += ConnectionPlan("${keys.last()}_lock", "exit", "doorway")

        return MapPlan(profile = input, topology = nodes, connections = connections)
    }
}

class ConnectorDoorAgent : MapSubagent<MapPlan, MapPlan> {
    override val name: String = "connector-door-agent"

    override fun run(input: MapPlan): MapPlan {
        val nodesById = input.topology.associateBy { it.id }
        val normalized = linkedMapOf<Pair<String, String>, ConnectionPlan>()

        input.connections.forEach { connection ->
            val target = nodesById[connection.to]
            val requiredKey = connection.requiredKey ?: target?.lock
            val normalizedConnection = if (connection.kind == "locked_door" && requiredKey != null) {
                connection.copy(requiredKey = requiredKey, notes = connection.notes.ifBlank { "Locked by the $requiredKey key." })
            } else {
                connection
            }
            normalized[connection.from to connection.to] = normalizedConnection

            if (target?.grantsKey != null) {
                normalized.putIfAbsent(
                    connection.to to connection.from,
                    ConnectionPlan(
                        from = connection.to,
                        to = connection.from,
                        kind = "loopback",
                        notes = "Returns player to the main landmark after collecting the ${target.grantsKey} key."
                    )
                )
            }
            if (connection.kind == "locked_door") {
                normalized.putIfAbsent(
                    connection.to to connection.from,
                    ConnectionPlan(
                        from = connection.to,
                        to = connection.from,
                        kind = "loopback",
                        notes = "Shortcut back after clearing the locked wing."
                    )
                )
            }
        }

        return input.copy(connections = normalized.values.toList())
    }
}

class RoomLayoutAgent : MapSubagent<MapPlan, MapPlan> {
    override val name: String = "room-layout-agent"

    override fun run(input: MapPlan): MapPlan {
        val textures = ThemeTextures.forTheme(input.profile.theme)
        val rooms = input.topology.mapIndexed { index, node ->
            val size = sizeFor(
                role = node.role,
                roomCount = input.profile.targetRoomCount,
                keyCount = input.profile.requestedKeys.size,
                setPieces = input.profile.requestedSetPieces
            )
            RoomPlan(
                id = node.id,
                shape = shapeFor(node.role, index),
                width = size.first,
                height = size.second,
                floorHeight = floorFor(node.role, index),
                ceilingHeight = ceilingFor(node.role),
                light = lightFor(node.role, input.profile.lightingHint),
                floorTexture = textures.floor,
                ceilingTexture = textures.ceiling,
                wallTexture = textures.wall,
                encounterHint = encounterFor(node.role, input.profile.difficulty),
                traversalHint = traversalFor(node, input.profile.requestedSetPieces),
                landmark = landmarkFor(node, input.profile.theme)
            )
        }

        return input.copy(rooms = rooms)
    }

    private fun sizeFor(role: String, roomCount: Int, keyCount: Int, setPieces: List<String>): Pair<Int, Int> = when (role) {
        "start" -> 256 to 256
        "connector" -> 384 to 256
        "arena" -> when {
            "aisle-arena" in setPieces || keyCount >= 3 -> 1536 to 1024
            roomCount >= 8 -> 1024 to 768
            else -> 640 to 512
        }
        "key_room" -> 384 to 384
        "locked_room" -> 512 to 384
        "secret" -> 224 to 192
        "exit" -> 320 to 256
        else -> 384 to 320
    }

    private fun shapeFor(role: String, index: Int): String = when (role) {
        "arena" -> "cross"
        "connector" -> "hall"
        "key_room" -> if (index % 2 == 0) "l_shape" else "rectangle"
        "locked_room" -> "rectangle_with_alcoves"
        "secret" -> "closet"
        else -> "rectangle"
    }

    private fun floorFor(role: String, index: Int): Int = when (role) {
        "arena" -> 0
        "key_room" -> if (index % 2 == 0) 8 else -8
        "locked_room" -> 16
        "secret" -> 0
        else -> 0
    }

    private fun ceilingFor(role: String): Int = when (role) {
        "arena" -> 160
        "locked_room" -> 144
        else -> 128
    }

    private fun lightFor(role: String, hint: String): Int {
        val base = when (hint) {
            "dark" -> 112
            "bright" -> 192
            else -> 160
        }
        return when (role) {
            "secret" -> (base - 32).coerceAtLeast(80)
            "exit" -> (base + 32).coerceAtMost(240)
            "key_room" -> (base + 16).coerceAtMost(224)
            else -> base
        }
    }

    private fun encounterFor(role: String, difficulty: String): String {
        val pressure = when (difficulty) {
            "easy" -> "light"
            "hard" -> "heavy"
            else -> "moderate"
        }
        return when (role) {
            "start" -> "no monsters; player gets bearings"
            "arena" -> "$pressure crossfire with cover and room to dodge"
            "key_room" -> "$pressure ambush after key pickup"
            "locked_room" -> "$pressure forward fight, not hitscan sniping"
            "secret" -> "reward only"
            "exit" -> "small final guard group"
            else -> "$pressure incidental patrol"
        }
    }

    private fun traversalFor(node: RoomNode, setPieces: List<String>): String = when {
        node.role == "arena" && "aisle-arena" in setPieces -> "parallel shelf aisles with cross-cuts wide enough for monsters and player movement"
        node.role == "locked_room" && "bridge" in setPieces -> "simple bridge segment with an obvious fallback route"
        node.role == "key_room" && "hazard-floor" in setPieces -> "small lowered hazard pool with a clear escape lip"
        node.role == "locked_room" && "switch" in setPieces -> "switch opens a return shortcut after the fight"
        else -> "flat passable floor with readable door approach"
    }

    private fun landmarkFor(node: RoomNode, theme: String): String = when (node.role) {
        "start" -> "player-facing ${theme.readable()} sign"
        "arena" -> "central landmark visible from each entrance"
        "key_room" -> "${node.grantsKey ?: "key"} pedestal in clear light"
        "locked_room" -> "${node.lock ?: "key"} color trim around the lock"
        "secret" -> "misaligned wall texture"
        "exit" -> "bright exit door and contrasting floor"
        else -> "texture break that marks progression"
    }
}

class MapPlanValidator : MapSubagent<MapPlan, MapPlan> {
    override val name: String = "map-plan-validator"

    override fun run(input: MapPlan): MapPlan {
        val hardIssues = mutableListOf<String>()
        val softIssues = mutableListOf<String>()
        val roomIds = input.topology.map { it.id }.toSet()
        val roomsById = input.topology.associateBy { it.id }
        val grantedKeys = input.topology.mapNotNull { it.grantsKey }.toSet()

        if (input.topology.none { it.role == "start" }) hardIssues += "Missing start room."
        if (input.topology.none { it.role == "exit" }) hardIssues += "Missing exit room."
        input.connections.forEach { connection ->
            if (connection.from !in roomIds) hardIssues += "Connection starts from unknown room ${connection.from}."
            if (connection.to !in roomIds) hardIssues += "Connection ends at unknown room ${connection.to}."
            if (connection.requiredKey != null && connection.requiredKey !in grantedKeys) {
                hardIssues += "Connection ${connection.from}->${connection.to} requires ${connection.requiredKey} key, but no room grants it."
            }
        }

        val reachableRooms = reachableRooms(input, roomsById)
        input.topology.filter { it.required }.forEach { room ->
            if (room.id !in reachableRooms) hardIssues += "Required room ${room.id} is not reachable with available keys."
        }
        if (input.topology.any { it.role == "exit" && it.id !in reachableRooms }) hardIssues += "Exit is not reachable."

        val layoutsById = input.rooms.associateBy { it.id }
        input.topology.forEach { node ->
            if (node.id !in layoutsById) hardIssues += "No room layout for ${node.id}."
        }
        input.rooms.forEach { room ->
            if (room.width < 128 || room.height < 128) hardIssues += "Room ${room.id} is too small for Doom movement."
            if (room.ceilingHeight - room.floorHeight < 80) hardIssues += "Room ${room.id} has too little vertical clearance."
            if (room.light !in 80..240) softIssues += "Room ${room.id} light ${room.light} is outside the preferred readable range."
        }
        if (input.connections.count { it.kind == "loopback" } == 0) softIssues += "No loopback connection yet; map may feel linear."
        if (input.topology.count { it.role == "secret" } == 0) softIssues += "No optional secret yet."

        val score = (100 - hardIssues.size * 25 - softIssues.size * 6).coerceIn(0, 100)
        return input.copy(
            validation = ValidationReport(
                passed = hardIssues.isEmpty() && score >= 70,
                score = score,
                hardIssues = hardIssues,
                softIssues = softIssues
            )
        )
    }

    private fun reachableRooms(input: MapPlan, roomsById: Map<String, RoomNode>): Set<String> {
        val start = input.topology.firstOrNull { it.role == "start" }?.id ?: return emptySet()
        val reachable = mutableSetOf(start)
        val keys = mutableSetOf<String>()
        var changed: Boolean

        do {
            changed = false
            reachable.toList().forEach { roomId ->
                roomsById[roomId]?.grantsKey?.let { key ->
                    if (keys.add(key)) changed = true
                }
            }
            input.connections
                .filter { it.from in reachable }
                .filter { it.requiredKey == null || it.requiredKey in keys }
                .forEach { connection ->
                    if (reachable.add(connection.to)) changed = true
                }
        } while (changed)

        return reachable
    }
}

class RootMapOrchestrator(
    private val promptProfileAgent: PromptProfileAgent = PromptProfileAgent(),
    private val topologyAgent: TopologyAgent = TopologyAgent(),
    private val connectorDoorAgent: ConnectorDoorAgent = ConnectorDoorAgent(),
    private val roomLayoutAgent: RoomLayoutAgent = RoomLayoutAgent(),
    private val validator: MapPlanValidator = MapPlanValidator()
) {
    fun generate(prompt: String): MapPlan {
        val profile = promptProfileAgent.run(prompt)
        val topologyPlan = topologyAgent.run(profile)
        val connectorPlan = connectorDoorAgent.run(topologyPlan)
        val roomPlan = roomLayoutAgent.run(connectorPlan)
        return validator.run(roomPlan)
    }
}

private data class ThemeTextures(
    val floor: String,
    val ceiling: String,
    val wall: String
) {
    companion object {
        fun forTheme(theme: String): ThemeTextures = when (theme) {
            "warehouse" -> ThemeTextures("FLOOR0_1", "CEIL1_1", "BROWN1")
            "hell" -> ThemeTextures("LAVA1", "CEIL1_1", "SKINFACE")
            "stone" -> ThemeTextures("FLOOR0_3", "CEIL3_5", "STONE2")
            else -> ThemeTextures("FLOOR0_1", "CEIL1_1", "STARTAN2")
        }
    }
}

private fun String.capitalized(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun String.readable(): String = replace('_', ' ')
