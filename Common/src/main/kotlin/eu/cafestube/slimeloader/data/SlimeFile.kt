package eu.cafestube.slimeloader.data

import net.kyori.adventure.nbt.CompoundBinaryTag

data class SlimeFile(
    val worldVersion: Int,
    val chunkMinX: Short,
    val chunkMinZ: Short,
    val width: Int,
    val depth: Int,

    val extraTag: CompoundBinaryTag?,
    val chunks: Map<Long, SlimeChunk>
)