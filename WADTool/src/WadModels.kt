package wadtool

/**
 * Models for DOOM/DOOM2 WAD file format structures.
 * These data classes represent the various components found in WAD files.
 */

/**
 * Represents a vertex in the level geometry.
 * Vertices are 2D points that define the corners of walls and other structures.
 */
data class Vertex(
    val x: Short, // X coordinate
    val y: Short  // Y coordinate
) {
    override fun toString(): String = "Vertex(x=$x, y=$y)"
}

/**
 * Represents a linedef, which is a line between two vertices.
 * Linedefs define walls and other boundaries in the level.
 */
data class LineDef(
    val startVertex: Int,    // Index of start vertex
    val endVertex: Int,      // Index of end vertex
    val flags: Short,        // Behavior flags
    val specialType: Short,  // Special effect type
    val sectorTag: Short,    // Sector tag for special effects
    val rightSideDef: Int,   // Index of right sidedef
    val leftSideDef: Int     // Index of left sidedef (-1 if none)
) {
    override fun toString(): String = "LineDef(start=$startVertex, end=$endVertex, flags=$flags, " +
            "specialType=$specialType, sectorTag=$sectorTag, rightSide=$rightSideDef, leftSide=$leftSideDef)"
}

/**
 * Represents a sidedef, which defines the texture information for one side of a linedef.
 */
data class SideDef(
    val xOffset: Short,         // X offset for texture
    val yOffset: Short,         // Y offset for texture
    val upperTexture: String,   // Upper texture name
    val lowerTexture: String,   // Lower texture name
    val middleTexture: String,  // Middle texture name
    val sector: Int             // Sector this sidedef faces
) {
    override fun toString(): String = "SideDef(xOffset=$xOffset, yOffset=$yOffset, " +
            "upperTexture='$upperTexture', lowerTexture='$lowerTexture', " +
            "middleTexture='$middleTexture', sector=$sector)"
}

/**
 * Represents a sector, which defines a volume in the level with specific properties.
 */
data class Sector(
    val floorHeight: Short,     // Floor height
    val ceilingHeight: Short,   // Ceiling height
    val floorTexture: String,   // Floor texture name
    val ceilingTexture: String, // Ceiling texture name
    val lightLevel: Short,      // Light level (0-255)
    val specialType: Short,     // Special effect type
    val tag: Short              // Tag number for special effects
) {
    override fun toString(): String = "Sector(floorHeight=$floorHeight, ceilingHeight=$ceilingHeight, " +
            "floorTexture='$floorTexture', ceilingTexture='$ceilingTexture', " +
            "lightLevel=$lightLevel, specialType=$specialType, tag=$tag)"
}

/**
 * Represents a thing, which is an entity in the level (player start, monster, item, etc.).
 */
data class Thing(
    val x: Short,       // X position
    val y: Short,       // Y position
    val angle: Short,   // Facing angle (0-359)
    val type: Short,    // Thing type (monster, item, etc.)
    val flags: Short    // Behavior flags
) {
    override fun toString(): String = "Thing(x=$x, y=$y, angle=$angle, type=$type, flags=$flags)"
}

/**
 * Represents a node in the BSP tree used for rendering.
 */
data class Node(
    val xPartition: Short,    // X coordinate of partition line start
    val yPartition: Short,    // Y coordinate of partition line start
    val xChange: Short,       // X change (dx) of partition line
    val yChange: Short,       // Y change (dy) of partition line
    val rightBoxTop: Short,   // Top of right bounding box
    val rightBoxBottom: Short,// Bottom of right bounding box
    val rightBoxLeft: Short,  // Left of right bounding box
    val rightBoxRight: Short, // Right of right bounding box
    val leftBoxTop: Short,    // Top of left bounding box
    val leftBoxBottom: Short, // Bottom of left bounding box
    val leftBoxLeft: Short,   // Left of left bounding box
    val leftBoxRight: Short,  // Right of left bounding box
    val rightChild: Int,      // Right child node or subsector
    val leftChild: Int        // Left child node or subsector
) {
    override fun toString(): String = "Node(partition=($xPartition,$yPartition), change=($xChange,$yChange), " +
            "rightBox=[$rightBoxLeft,$rightBoxBottom,$rightBoxRight,$rightBoxTop], " +
            "leftBox=[$leftBoxLeft,$leftBoxBottom,$leftBoxRight,$leftBoxTop], " +
            "rightChild=$rightChild, leftChild=$leftChild)"
}

/**
 * Represents a subsector, which is a convex sector fragment.
 */
data class SubSector(
    val segCount: Short,  // Number of segs in this subsector
    val firstSeg: Int     // Index of first seg
) {
    override fun toString(): String = "SubSector(segCount=$segCount, firstSeg=$firstSeg)"
}

/**
 * Represents a seg, which is a line segment that makes up part of a subsector.
 */
data class Seg(
    val startVertex: Int,   // Index of start vertex
    val endVertex: Int,     // Index of end vertex
    val angle: Short,       // Angle of this seg
    val lineDef: Int,       // Index of parent linedef
    val direction: Short,   // 0 (same as linedef) or 1 (opposite of linedef)
    val offset: Short       // Distance along linedef to start of seg
) {
    override fun toString(): String = "Seg(start=$startVertex, end=$endVertex, angle=$angle, " +
            "lineDef=$lineDef, direction=$direction, offset=$offset)"
}