package gg.astromc.slimeloader.loader

import eu.cafestube.slimeloader.data.SlimeFile
import eu.cafestube.slimeloader.loader.serialize
import eu.cafestube.slimeloader.pruner.canBePruned
import gg.astromc.slimeloader.data.NoOpSlimeFixer
import gg.astromc.slimeloader.data.SlimeDataFixer
import gg.astromc.slimeloader.source.SlimeSource
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import java.io.DataOutputStream

class SlimeLoader(
    val slimeSource: SlimeSource?,
    val slimeFile: SlimeFile
) : IChunkLoader {

    constructor(slimeSource: SlimeSource, readOnly: Boolean = false, dataFixer: SlimeDataFixer = NoOpSlimeFixer)
            : this(if(readOnly) null else slimeSource, dataFixer.fixWorld(slimeSource.loadWorld()))

    override fun loadInstance(instance: Instance) {
        instance.setTag(Tag.NBT("Data"), this.slimeFile.extraTag)
    }

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): Chunk? {
        val slimeChunk = slimeFile.getChunk(chunkX, chunkZ) ?: return null

        val chunk = instance.chunkSupplier.createChunk(instance, chunkX, chunkZ)
        slimeChunk.sections.forEach { slimeSection ->
            val section = chunk.sections[slimeSection.index]

            applyChunkSection(chunk, section, slimeSection)
        }
        applyBlockEntities(chunk, slimeChunk)

        return chunk
    }

    override fun supportsParallelSaving(): Boolean {
        return false
    }

    override fun saveChunk(chunk: Chunk) {
        //Even if the world is read-only, we still need to save the chunk to the in memory slime file
        //so chunk-reloads work properly

        val slime = toSlimeChunk(chunk)
        if(slime.canBePruned()) {
            slimeFile.removeChunk(chunk.chunkX, chunk.chunkZ)
            return
        }
        slimeFile.setChunk(chunk.chunkX, chunk.chunkZ, slime)
    }

    override fun saveInstance(instance: Instance) {
        if (slimeSource == null) return

        val outputStream = slimeSource.save()
        val dataOutputStream = DataOutputStream(outputStream)

        serialize(dataOutputStream, slimeFile)
        dataOutputStream.flush()
        dataOutputStream.close()
    }

}
