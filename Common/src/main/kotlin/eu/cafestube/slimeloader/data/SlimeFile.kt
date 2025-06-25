package eu.cafestube.slimeloader.data

import eu.cafestube.slimeloader.helpers.getChunkIndex
import eu.cafestube.slimeloader.loader.V13AdditionalWorldData
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.util.EnumSet

data class SlimeFile(
    val worldVersion: Int,
    val chunkFlags: EnumSet<V13AdditionalWorldData>,

    val chunkMinX: Short,
    val chunkMinZ: Short,
    val width: Int,
    val depth: Int,

    val extraTag: CompoundBinaryTag?,
    val chunks: Map<Long, SlimeChunk>
) {

    fun getChunk(x: Int, z: Int): SlimeChunk? {
        return chunks[getChunkIndex(x, z)]
    }

}