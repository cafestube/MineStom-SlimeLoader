package eu.cafestube.slimeloader.data

import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag

class SlimeChunk(
    val x: Int,
    val z: Int,
    val sections: Array<SlimeSection>,
    val heightMaps: CompoundBinaryTag,

    val poiChunk: CompoundBinaryTag?,
    val blockTicks: ListBinaryTag?,
    val fluidTicks: ListBinaryTag?,

    val tileEntities: ListBinaryTag,
    val entities: ListBinaryTag,
    val extra: CompoundBinaryTag
)