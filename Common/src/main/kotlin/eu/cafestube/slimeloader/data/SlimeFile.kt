package eu.cafestube.slimeloader.data

import eu.cafestube.slimeloader.helpers.getChunkIndex
import eu.cafestube.slimeloader.loader.V13AdditionalWorldData
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.util.EnumSet

data class SlimeFile(
    val worldVersion: Int,
    val chunkFlags: EnumSet<V13AdditionalWorldData>,

    var extraTag: CompoundBinaryTag?,
    val chunks: MutableMap<Long, SlimeChunk>
) {

    fun clone(): SlimeFile {
        return SlimeFile(
            worldVersion,
            chunkFlags,
            extraTag,
            chunks.toMutableMap()
        )
    }

    fun setChunk(x: Int, z: Int, chunk: SlimeChunk) {
        chunks[getChunkIndex(x, z)] = chunk
    }

    fun getChunk(x: Int, z: Int): SlimeChunk? {
        return chunks[getChunkIndex(x, z)]
    }

    fun removeChunk(chunkX: Int, chunkZ: Int) {
        chunks.remove(getChunkIndex(chunkX, chunkZ))
    }

}