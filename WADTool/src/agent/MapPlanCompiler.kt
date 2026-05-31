package wadtool.agent

import wadtool.LevelBuilder
import wadtool.LineDef
import wadtool.MapAnnotation
import wadtool.Node
import wadtool.Sector
import wadtool.Seg
import wadtool.SideDef
import wadtool.SubSector
import wadtool.Thing
import wadtool.Vertex

class MapPlanCompiler {
    fun compile(plan: MapPlan): LevelBuilder.BuiltLevel {
        require(plan.rooms.isNotEmpty()) { "MapPlan has no room layouts to compile." }

        val roomLookup = plan.rooms.associateBy { it.id }
        val placements = placeRooms(plan, roomLookup)
        val sectors = plan.rooms.map { room ->
            Sector(
                floorHeight = room.floorHeight.toShort(),
                ceilingHeight = room.ceilingHeight.toShort(),
                floorTexture = room.floorTexture.wadName(),
                ceilingTexture = room.ceilingTexture.wadName(),
                lightLevel = room.light.toShort(),
                specialType = 0,
                tag = 0
            )
        }
        val sectorIndexByRoom = plan.rooms.mapIndexed { index, room -> room.id to index }.toMap()

        val builder = GeometryBuilder()
        val portals = buildPortals(plan, placements, sectorIndexByRoom)
        val portalIntervals = mutableMapOf<RoomSide, MutableList<IntRange>>()
        portals.forEach { portal ->
            portalIntervals.getOrPut(RoomSide(portal.fromRoom, portal.fromSide)) { mutableListOf() } += portal.interval
            portalIntervals.getOrPut(RoomSide(portal.toRoom, portal.toSide)) { mutableListOf() } += portal.interval
            builder.addPortal(portal)
        }

        plan.rooms.forEach { room ->
            val rect = placements.getValue(room.id)
            val sectorIndex = sectorIndexByRoom.getValue(room.id)
            builder.addRoomWalls(
                room = room,
                rect = rect,
                sectorIndex = sectorIndex,
                blockedIntervals = portalIntervals,
            )
        }

        val things = buildThings(plan, placements)
        val bsp = SimpleBspBuilder(
            lineDefs = builder.lineDefs,
            sideDefs = builder.sideDefs,
            vertexes = builder.vertexes,
            sectorRects = plan.rooms.mapIndexed { index, room -> index to placements.getValue(room.id) }
        ).build()

        return LevelBuilder.BuiltLevel(
            vertexes = builder.vertexes,
            lineDefs = builder.lineDefs,
            sideDefs = builder.sideDefs,
            sectors = sectors,
            things = things,
            nodes = bsp.nodes,
            subSectors = bsp.subSectors,
            segs = bsp.segs,
            annotations = buildRoomAnnotations(plan, placements)
        )
    }

    private fun placeRooms(plan: MapPlan, roomLookup: Map<String, RoomPlan>): Map<String, Rect> {
        val startId = plan.topology.firstOrNull { it.role == "start" }?.id ?: plan.rooms.first().id
        val startRoom = roomLookup.getValue(startId)
        val placed = linkedMapOf(startId to Rect(0, 0, startRoom.width.grid(), startRoom.height.grid()))
        val directionsByRoom = mutableMapOf<String, Int>()

        var changed: Boolean
        do {
            changed = false
            plan.connections.forEach { connection ->
                if (connection.from in placed && connection.to !in placed) {
                    val target = roomLookup[connection.to] ?: return@forEach
                    placed[connection.to] = findPlacement(
                        source = placed.getValue(connection.from),
                        target = target,
                        preferred = preferredDirections(target, connection, directionsByRoom[connection.from] ?: 0),
                        placed = placed.values.toList()
                    )
                    directionsByRoom[connection.from] = (directionsByRoom[connection.from] ?: 0) + 1
                    changed = true
                }
                if (connection.to in placed && connection.from !in placed) {
                    val target = roomLookup[connection.from] ?: return@forEach
                    placed[connection.from] = findPlacement(
                        source = placed.getValue(connection.to),
                        target = target,
                        preferred = preferredDirections(target, connection, directionsByRoom[connection.to] ?: 0),
                        placed = placed.values.toList()
                    )
                    directionsByRoom[connection.to] = (directionsByRoom[connection.to] ?: 0) + 1
                    changed = true
                }
            }
        } while (changed)

        plan.rooms.filterNot { it.id in placed }.forEachIndexed { index, room ->
            val previous = placed.values.last()
            placed[room.id] = Rect(previous.right, index * 768, previous.right + room.width.grid(), index * 768 + room.height.grid())
        }

        return placed
    }

    private fun preferredDirections(room: RoomPlan, connection: ConnectionPlan, usedDirections: Int): List<Direction> {
        val primary = when {
            connection.kind == "secret_door" -> listOf(Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST)
            room.id.endsWith("_key") -> if (usedDirections % 2 == 0) {
                listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)
            } else {
                listOf(Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST)
            }
            room.roleLikeExit() || room.id.endsWith("_lock") -> listOf(Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.WEST)
            else -> listOf(Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST)
        }
        return primary
    }

    private fun findPlacement(source: Rect, target: RoomPlan, preferred: List<Direction>, placed: List<Rect>): Rect {
        val width = target.width.grid()
        val height = target.height.grid()
        val directions = preferred + Direction.values().filterNot { it in preferred }
        val candidates = directions.flatMap { direction ->
            placementOffsets().map { offset ->
                touchingRect(source, width, height, direction, offset)
            }
        }

        return candidates.firstOrNull { candidate ->
            candidate.touchOverlap(source) >= MIN_PORTAL_OVERLAP && placed.none { it.overlaps(candidate) }
        } ?: error("Could not place room ${target.id} next to its source without overlap. Increase hub size or add connector rooms.")
    }

    private fun placementOffsets(): List<Int> = buildList {
        add(0)
        (1..18).forEach { step ->
            add(-step)
            add(step)
        }
    }

    private fun touchingRect(source: Rect, width: Int, height: Int, direction: Direction, offset: Int): Rect {
        val shift = offset * PLACEMENT_GRID
        return when (direction) {
            Direction.EAST -> {
                val bottom = source.centerY - height / 2 + shift
                Rect(source.right, bottom, source.right + width, bottom + height)
            }
            Direction.WEST -> {
                val bottom = source.centerY - height / 2 + shift
                Rect(source.left - width, bottom, source.left, bottom + height)
            }
            Direction.NORTH -> {
                val left = source.centerX - width / 2 + shift
                Rect(left, source.top, left + width, source.top + height)
            }
            Direction.SOUTH -> {
                val left = source.centerX - width / 2 + shift
                Rect(left, source.bottom - height, left + width, source.bottom)
            }
        }
    }

    private fun buildPortals(
        plan: MapPlan,
        placements: Map<String, Rect>,
        sectorIndexByRoom: Map<String, Int>
    ): List<Portal> {
        val portals = mutableListOf<Portal>()
        val seenPairs = mutableSetOf<Set<String>>()

        plan.connections.forEach { connection ->
            val from = placements[connection.from] ?: return@forEach
            val to = placements[connection.to] ?: return@forEach
            val pair = setOf(connection.from, connection.to)
            if (!seenPairs.add(pair)) return@forEach

            val portal = touchingPortal(
                fromRoom = connection.from,
                fromRect = from,
                fromSector = sectorIndexByRoom.getValue(connection.from),
                toRoom = connection.to,
                toRect = to,
                toSector = sectorIndexByRoom.getValue(connection.to),
                connection = connection
            )
            if (portal != null) portals += portal
        }

        return portals
    }

    private fun touchingPortal(
        fromRoom: String,
        fromRect: Rect,
        fromSector: Int,
        toRoom: String,
        toRect: Rect,
        toSector: Int,
        connection: ConnectionPlan
    ): Portal? {
        if (fromRect.right == toRect.left) {
            return verticalPortal(fromRoom, Side.EAST, fromSector, toRoom, Side.WEST, toSector, fromRect.right, fromRect, toRect, connection)
        }
        if (fromRect.left == toRect.right) {
            return verticalPortal(fromRoom, Side.WEST, fromSector, toRoom, Side.EAST, toSector, fromRect.left, fromRect, toRect, connection)
        }
        if (fromRect.top == toRect.bottom) {
            return horizontalPortal(fromRoom, Side.NORTH, fromSector, toRoom, Side.SOUTH, toSector, fromRect.top, fromRect, toRect, connection)
        }
        if (fromRect.bottom == toRect.top) {
            return horizontalPortal(fromRoom, Side.SOUTH, fromSector, toRoom, Side.NORTH, toSector, fromRect.bottom, fromRect, toRect, connection)
        }
        return null
    }

    private fun verticalPortal(
        fromRoom: String,
        fromSide: Side,
        fromSector: Int,
        toRoom: String,
        toSide: Side,
        toSector: Int,
        x: Int,
        fromRect: Rect,
        toRect: Rect,
        connection: ConnectionPlan
    ): Portal? {
        val overlap = maxOf(fromRect.bottom, toRect.bottom)..minOf(fromRect.top, toRect.top)
        if (overlap.last - overlap.first < 96) return null
        val interval = centeredInterval(overlap, widthFor(connection))
        val startY = if (fromSide == Side.EAST) interval.last else interval.first
        val endY = if (fromSide == Side.EAST) interval.first else interval.last
        return Portal(
            fromRoom = fromRoom,
            fromSide = fromSide,
            fromSector = fromSector,
            toRoom = toRoom,
            toSide = toSide,
            toSector = toSector,
            start = Point(x, startY),
            end = Point(x, endY),
            interval = interval,
            connection = connection
        )
    }

    private fun horizontalPortal(
        fromRoom: String,
        fromSide: Side,
        fromSector: Int,
        toRoom: String,
        toSide: Side,
        toSector: Int,
        y: Int,
        fromRect: Rect,
        toRect: Rect,
        connection: ConnectionPlan
    ): Portal? {
        val overlap = maxOf(fromRect.left, toRect.left)..minOf(fromRect.right, toRect.right)
        if (overlap.last - overlap.first < 96) return null
        val interval = centeredInterval(overlap, widthFor(connection))
        val startX = if (fromSide == Side.SOUTH) interval.last else interval.first
        val endX = if (fromSide == Side.SOUTH) interval.first else interval.last
        return Portal(
            fromRoom = fromRoom,
            fromSide = fromSide,
            fromSector = fromSector,
            toRoom = toRoom,
            toSide = toSide,
            toSector = toSector,
            start = Point(startX, y),
            end = Point(endX, y),
            interval = interval,
            connection = connection
        )
    }

    private fun centeredInterval(overlap: IntRange, desiredWidth: Int): IntRange {
        val available = overlap.last - overlap.first
        val width = desiredWidth.coerceAtMost(available - 32).coerceAtLeast(96)
        val center = (overlap.first + overlap.last) / 2
        return (center - width / 2)..(center + width / 2)
    }

    private fun widthFor(connection: ConnectionPlan): Int = when (connection.kind) {
        "secret_door" -> 96
        "locked_door" -> 128
        else -> 160
    }

    private fun buildThings(plan: MapPlan, placements: Map<String, Rect>): List<Thing> {
        val things = mutableListOf<Thing>()
        val lowerPrompt = plan.profile.originalPrompt.lowercase()
        val startRoomId = when {
            listOf("center of the huge room", "center of room a", "inside in the center of the room").any { it in lowerPrompt } -> {
                plan.topology.firstOrNull { it.role == "arena" }?.id
            }
            else -> plan.topology.firstOrNull { it.role == "start" }?.id
        } ?: plan.rooms.first().id

        placements[startRoomId]?.let { start ->
            things += Thing(start.centerX.toShort(), start.centerY.toShort(), 0, 1, 7)
        }

        if ("imp" in lowerPrompt) {
            val impCount = Regex("""\b([1-9])\s+imps?\b""").find(lowerPrompt)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 4
            val arenaId = plan.topology.firstOrNull { it.role == "arena" }?.id
            placements[arenaId]?.let { arena ->
                things += cornerThings(arena, impCount.coerceIn(1, 8), type = 3001)
            }
        }

        plan.topology.forEach { node ->
            val rect = placements[node.id] ?: return@forEach
            node.grantsKey?.let { key ->
                things += Thing(rect.centerX.toShort(), rect.centerY.toShort(), 0, keyThingType(key), 7)
            }
            if (node.role == "exit") {
                things += Thing((rect.centerX + 48).toShort(), rect.centerY.toShort(), 0, 2015, 7)
            }
        }
        return things
    }

    private fun cornerThings(rect: Rect, count: Int, type: Short): List<Thing> {
        val inset = 96
        val corners = listOf(
            Point(rect.left + inset, rect.bottom + inset),
            Point(rect.right - inset, rect.bottom + inset),
            Point(rect.right - inset, rect.top - inset),
            Point(rect.left + inset, rect.top - inset),
            Point(rect.centerX, rect.top - inset),
            Point(rect.right - inset, rect.centerY),
            Point(rect.centerX, rect.bottom + inset),
            Point(rect.left + inset, rect.centerY)
        )

        return corners.take(count).map { point ->
            Thing(point.x.toShort(), point.y.toShort(), 0, type, 7)
        }
    }

    private fun keyThingType(key: String): Short = when (key) {
        "blue" -> 5
        "yellow" -> 6
        "red" -> 13
        else -> 5
    }.toShort()

    private fun buildRoomAnnotations(plan: MapPlan, placements: Map<String, Rect>): List<MapAnnotation> {
        val nodesById = plan.topology.associateBy { it.id }
        return plan.rooms.mapIndexedNotNull { index, room ->
            val rect = placements[room.id] ?: return@mapIndexedNotNull null
            val node = nodesById[room.id]
            MapAnnotation(
                text = (index + 1).toString(),
                x = rect.centerX,
                y = rect.centerY,
                title = listOfNotNull(
                    node?.label,
                    node?.role?.let { "role=$it" },
                    "id=${room.id}"
                ).joinToString(" | ")
            )
        }
    }
}

private class GeometryBuilder {
    private val vertexIndexByPoint = linkedMapOf<Point, Int>()
    val vertexes = mutableListOf<Vertex>()
    val sideDefs = mutableListOf<SideDef>()
    val lineDefs = mutableListOf<LineDef>()

    fun addPortal(portal: Portal) {
        val right = addSideDef(portal.fromSector, "-", "-", "-")
        val left = addSideDef(portal.toSector, portal.toTexture(), portal.toTexture(), "-")
        lineDefs += LineDef(
            startVertex = vertexIndex(portal.start),
            endVertex = vertexIndex(portal.end),
            flags = 4,
            specialType = 0,
            sectorTag = 0,
            rightSideDef = right,
            leftSideDef = left
        )
    }

    fun addRoomWalls(
        room: RoomPlan,
        rect: Rect,
        sectorIndex: Int,
        blockedIntervals: Map<RoomSide, List<IntRange>>
    ) {
        val wallTexture = room.wallTexture.wadName()
        addSideSegments(room.id, Side.SOUTH, rect.left, rect.right, rect.bottom, sectorIndex, wallTexture, blockedIntervals)
        addSideSegments(room.id, Side.EAST, rect.bottom, rect.top, rect.right, sectorIndex, wallTexture, blockedIntervals)
        addSideSegments(room.id, Side.NORTH, rect.left, rect.right, rect.top, sectorIndex, wallTexture, blockedIntervals)
        addSideSegments(room.id, Side.WEST, rect.bottom, rect.top, rect.left, sectorIndex, wallTexture, blockedIntervals)
    }

    private fun addSideSegments(
        roomId: String,
        side: Side,
        start: Int,
        end: Int,
        fixed: Int,
        sectorIndex: Int,
        wallTexture: String,
        blockedIntervals: Map<RoomSide, List<IntRange>>
    ) {
        val intervals = blockedIntervals[RoomSide(roomId, side)].orEmpty()
        val openSegments = subtractIntervals(start..end, intervals)
        openSegments.forEach { segment ->
            if (segment.last - segment.first < 8) return@forEach
            val lineStart = startPointFor(side, segment, fixed)
            val lineEnd = endPointFor(side, segment, fixed)
            val sideDef = addSideDef(sectorIndex, "-", "-", wallTexture)
            lineDefs += LineDef(
                startVertex = vertexIndex(lineStart),
                endVertex = vertexIndex(lineEnd),
                flags = 1,
                specialType = 0,
                sectorTag = 0,
                rightSideDef = sideDef,
                leftSideDef = -1
            )
        }
    }

    private fun startPointFor(side: Side, segment: IntRange, fixed: Int): Point = when (side) {
        Side.SOUTH -> Point(segment.last, fixed)
        Side.EAST -> Point(fixed, segment.last)
        Side.NORTH -> Point(segment.first, fixed)
        Side.WEST -> Point(fixed, segment.first)
    }

    private fun endPointFor(side: Side, segment: IntRange, fixed: Int): Point = when (side) {
        Side.SOUTH -> Point(segment.first, fixed)
        Side.EAST -> Point(fixed, segment.first)
        Side.NORTH -> Point(segment.last, fixed)
        Side.WEST -> Point(fixed, segment.last)
    }

    private fun addSideDef(sectorIndex: Int, upper: String, lower: String, middle: String): Int {
        val index = sideDefs.size
        sideDefs += SideDef(0, 0, upper.wadName(), lower.wadName(), middle.wadName(), sectorIndex)
        return index
    }

    private fun vertexIndex(point: Point): Int =
        vertexIndexByPoint.getOrPut(point) {
            vertexes += Vertex(point.x.toShort(), point.y.toShort())
            vertexes.lastIndex
        }

    private fun subtractIntervals(bounds: IntRange, blocked: List<IntRange>): List<IntRange> {
        val normalized = blocked
            .map { maxOf(bounds.first, it.first)..minOf(bounds.last, it.last) }
            .filter { it.first < it.last }
            .sortedBy { it.first }

        val segments = mutableListOf<IntRange>()
        var cursor = bounds.first
        normalized.forEach { interval ->
            if (cursor < interval.first) segments += cursor..interval.first
            cursor = maxOf(cursor, interval.last)
        }
        if (cursor < bounds.last) segments += cursor..bounds.last
        return segments
    }
}

private class SimpleBspBuilder(
    private val lineDefs: List<LineDef>,
    private val sideDefs: List<SideDef>,
    private val vertexes: List<Vertex>,
    sectorRects: List<Pair<Int, Rect>>
) {
    private val sectors = sectorRects.map { (sectorIndex, rect) -> BspSector(sectorIndex, rect) }
    private val nodes = mutableListOf<Node>()

    fun build(): BspLumps {
        val segs = mutableListOf<Seg>()
        val subSectors = mutableListOf<SubSector>()

        sectors.sortedBy { it.sectorIndex }.forEach { sector ->
            val firstSeg = segs.size
            lineDefs.forEachIndexed { lineIndex, lineDef ->
                val rightSector = sideDefs.getOrNull(lineDef.rightSideDef)?.sector
                if (rightSector == sector.sectorIndex) {
                    segs += segFor(lineDef, lineIndex, direction = 0)
                }
                val leftSector = if (lineDef.leftSideDef >= 0) sideDefs.getOrNull(lineDef.leftSideDef)?.sector else null
                if (leftSector == sector.sectorIndex) {
                    segs += segFor(lineDef, lineIndex, direction = 1)
                }
            }
            subSectors += SubSector((segs.size - firstSeg).toShort(), firstSeg)
        }

        if (sectors.size > 1) {
            buildNode(sectors)
        }

        return BspLumps(nodes = nodes, subSectors = subSectors, segs = segs)
    }

    private fun buildNode(entries: List<BspSector>): Int {
        if (entries.size == 1) return SUBSECTOR_FLAG or entries.first().sectorIndex

        val bounds = boundsFor(entries)
        val splitVertical = (bounds.right - bounds.left) >= (bounds.top - bounds.bottom)
        val sorted = if (splitVertical) entries.sortedBy { it.rect.centerX } else entries.sortedBy { it.rect.centerY }
        val leftEntries = sorted.take(sorted.size / 2)
        val rightEntries = sorted.drop(sorted.size / 2)

        val leftChild = buildNode(leftEntries)
        val rightChild = buildNode(rightEntries)
        val leftBox = boundsFor(leftEntries)
        val rightBox = boundsFor(rightEntries)

        val node = if (splitVertical) {
            val splitX = (leftBox.right + rightBox.left) / 2
            Node(
                xPartition = splitX.toShort(),
                yPartition = bounds.bottom.toShort(),
                xChange = 0,
                yChange = (bounds.top - bounds.bottom).toShort(),
                rightBoxTop = rightBox.top.toShort(),
                rightBoxBottom = rightBox.bottom.toShort(),
                rightBoxLeft = rightBox.left.toShort(),
                rightBoxRight = rightBox.right.toShort(),
                leftBoxTop = leftBox.top.toShort(),
                leftBoxBottom = leftBox.bottom.toShort(),
                leftBoxLeft = leftBox.left.toShort(),
                leftBoxRight = leftBox.right.toShort(),
                rightChild = rightChild,
                leftChild = leftChild
            )
        } else {
            val splitY = (leftBox.top + rightBox.bottom) / 2
            Node(
                xPartition = bounds.left.toShort(),
                yPartition = splitY.toShort(),
                xChange = (bounds.right - bounds.left).toShort(),
                yChange = 0,
                rightBoxTop = rightBox.top.toShort(),
                rightBoxBottom = rightBox.bottom.toShort(),
                rightBoxLeft = rightBox.left.toShort(),
                rightBoxRight = rightBox.right.toShort(),
                leftBoxTop = leftBox.top.toShort(),
                leftBoxBottom = leftBox.bottom.toShort(),
                leftBoxLeft = leftBox.left.toShort(),
                leftBoxRight = leftBox.right.toShort(),
                rightChild = rightChild,
                leftChild = leftChild
            )
        }

        nodes += node
        return nodes.lastIndex
    }

    private fun segFor(lineDef: LineDef, lineIndex: Int, direction: Short): Seg {
        val startIndex = if (direction.toInt() == 0) lineDef.startVertex else lineDef.endVertex
        val endIndex = if (direction.toInt() == 0) lineDef.endVertex else lineDef.startVertex
        val start = vertexes[startIndex]
        val end = vertexes[endIndex]
        return Seg(
            startVertex = startIndex,
            endVertex = endIndex,
            angle = doomAngle(start, end),
            lineDef = lineIndex,
            direction = direction,
            offset = 0
        )
    }

    private fun doomAngle(start: Vertex, end: Vertex): Short {
        val radians = kotlin.math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val turns = radians / (Math.PI * 2.0)
        val value = ((turns * 65536.0).toInt() and 0xFFFF)
        return value.toShort()
    }

    private fun boundsFor(entries: List<BspSector>): Rect =
        Rect(
            left = entries.minOf { it.rect.left },
            bottom = entries.minOf { it.rect.bottom },
            right = entries.maxOf { it.rect.right },
            top = entries.maxOf { it.rect.top }
        )

    private data class BspSector(val sectorIndex: Int, val rect: Rect)

    companion object {
        private const val SUBSECTOR_FLAG = 0x8000
    }
}

private data class BspLumps(
    val nodes: List<Node>,
    val subSectors: List<SubSector>,
    val segs: List<Seg>
)

private data class Rect(val left: Int, val bottom: Int, val right: Int, val top: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (bottom + top) / 2

    fun overlaps(other: Rect): Boolean =
        left < other.right && right > other.left && bottom < other.top && top > other.bottom

    fun touchOverlap(other: Rect): Int = when {
        right == other.left || left == other.right -> minOf(top, other.top) - maxOf(bottom, other.bottom)
        top == other.bottom || bottom == other.top -> minOf(right, other.right) - maxOf(left, other.left)
        else -> 0
    }
}

private data class Point(val x: Int, val y: Int)

private data class RoomSide(val roomId: String, val side: Side)

private data class Portal(
    val fromRoom: String,
    val fromSide: Side,
    val fromSector: Int,
    val toRoom: String,
    val toSide: Side,
    val toSector: Int,
    val start: Point,
    val end: Point,
    val interval: IntRange,
    val connection: ConnectionPlan
) {
    fun toTexture(): String = when (connection.requiredKey) {
        "blue" -> "DOORBLU"
        "red" -> "DOORRED"
        "yellow" -> "DOORYEL"
        else -> "STARTAN2"
    }
}

private enum class Direction {
    EAST,
    NORTH,
    SOUTH,
    WEST
}

private enum class Side {
    SOUTH,
    EAST,
    NORTH,
    WEST
}

private fun RoomPlan.roleLikeExit(): Boolean = id == "exit" || encounterHint.contains("final", ignoreCase = true)

private fun Int.grid(): Int = coerceAtLeast(160).let { value -> ((value + 31) / 32) * 32 }

private fun String.wadName(): String = if (this == "-") "-" else take(8)

private const val PLACEMENT_GRID = 64
private const val MIN_PORTAL_OVERLAP = 96
