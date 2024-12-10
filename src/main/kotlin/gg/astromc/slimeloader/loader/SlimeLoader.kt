package gg.astromc.slimeloader.loader

import com.github.luben.zstd.Zstd
import eu.cafestube.slimeloader.helpers.ChunkHelpers.getChunkIndex
import eu.cafestube.slimeloader.helpers.NBTHelpers
import eu.cafestube.slimeloader.helpers.NBTHelpers.getUncompressedBiomeIndices
import gg.astromc.slimeloader.data.NoOpSlimeFixer
import gg.astromc.slimeloader.data.SlimeDataFixer
import gg.astromc.slimeloader.source.SlimeSource
import net.kyori.adventure.nbt.BinaryTagTypes
import net.kyori.adventure.nbt.ListBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.chunk.ChunkUtils.*
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.util.concurrent.CompletableFuture


class SlimeLoader(
    private val slimeSource: SlimeSource,
    private val readOnly: Boolean = false,
    private val slimeDataFixer: SlimeDataFixer = NoOpSlimeFixer,
) : IChunkLoader {

    private val logger = LoggerFactory.getLogger(SlimeLoader::class.java)
    private val slimeFile = slimeDataFixer.fixWorld(slimeSource.loadWorld())
    private val warnedBiomes = mutableSetOf<String>()

    override fun loadInstance(instance: Instance) {
        instance.setTag(Tag.NBT("Data"), this.slimeFile.extraTag)
    }

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): Chunk? {
        val slimeChunk = slimeFile.chunks[getChunkIndex(chunkX, chunkZ)] ?: return null

        val chunk = instance.chunkSupplier.createChunk(instance, chunkX, chunkZ)
        slimeChunk.sections.forEach { slimeSection ->
            val section = chunk.sections[slimeSection.index]
            if (slimeSection.skyLight != null)
                section.setSkyLight(slimeSection.skyLight)
            if (slimeSection.blockLight != null) {
                section.setBlockLight(slimeSection.blockLight)
            }


            val biomes = convertPalette(slimeSection.biomeTag.getList("palette"))

            if (biomes.isNotEmpty()) {
                if (biomes.size == 1) {
                    section.biomePalette().fill(biomes.first())
                } else {
                    val ids = getUncompressedBiomeIndices(slimeSection.biomeTag)
                    section.biomePalette().setAll { xx, yx, zx ->
                        val index: Int = xx + zx * 4 + yx * 16
                        biomes[ids[index]]
                    }
                }
            }

            //Blocks
            val blocks = slimeSection.blockStateTag
            val blockPalette = blocks.getList("palette")
            val blockData = NBTHelpers.loadBlockData(blocks)

            val convertedPalette = arrayOfNulls<Block>(blockPalette.size())
            for (i in convertedPalette.indices) {
                val paletteEntry = blockPalette.getCompound(i)
                val blockName = paletteEntry.getString("Name")
                if (blockName == "minecraft:air") {
                    convertedPalette[i] = Block.AIR
                    continue
                } else {
                    val properties = HashMap<String, String>()
                    val propertiesNbt = paletteEntry.getCompound("Properties")
                    for ((key, value) in propertiesNbt) {
                        if (value.type() != BinaryTagTypes.STRING) {
                            logger.warn(
                                "Fail to parse block state properties {}, expected a TAG_String for {}, but contents were {}",
                                propertiesNbt, key, value
                            )
                        } else {
                            properties[key] = (value as StringBinaryTag).value()
                        }
                    }
                    var block = Block.fromNamespaceId(NamespaceID.from(blockName))!!.let {
                        if (properties.isNotEmpty()) {
                            it.withProperties(properties)
                        } else {
                            it
                        }
                    }
                    // Handler
                    val handler = MinecraftServer.getBlockManager().getHandler(block.name())
                    if (handler != null) block = block.withHandler(handler)

                    convertedPalette[i] = block
                }
            }

            val dimensionType = MinecraftServer.getDimensionTypeRegistry().get(instance.dimensionType)!!

            for (y in 0 until Chunk.CHUNK_SECTION_SIZE) {
                for (z in 0 until Chunk.CHUNK_SECTION_SIZE) {
                    for (x in 0 until Chunk.CHUNK_SECTION_SIZE) {
                        try {
                            val blockIndex =
                                y * Chunk.CHUNK_SECTION_SIZE * Chunk.CHUNK_SECTION_SIZE + z * Chunk.CHUNK_SECTION_SIZE + x

                            if (blockData.size <= blockIndex) {
                                continue
                            }

                            val paletteIndex: Int = blockData[blockIndex]
                            val block = convertedPalette[paletteIndex]
                            if (block != null) {
                                chunk.setBlock(
                                    x,
                                    dimensionType.minY() + y + (Chunk.CHUNK_SECTION_SIZE * slimeSection.index),
                                    z,
                                    block
                                )
                            }
                        } catch (e: Exception) {
                            MinecraftServer.getExceptionManager().handleException(e)
                        }
                    }
                }
            }
        }

        return chunk
    }

    private fun convertPalette(list: ListBinaryTag): IntArray {
        val convertedPalette = IntArray(list.size())

        for (i in convertedPalette.indices) {
            val name: String = list.getString(i)
            var biomeId = MinecraftServer.getBiomeRegistry().getId(NamespaceID.from(name))
            if (biomeId == -1) {
                biomeId = MinecraftServer.getBiomeRegistry().getId(NamespaceID.from("minecraft", "plains"))
            }

            convertedPalette[i] = biomeId
        }

        return convertedPalette
    }


    private fun warnAboutMissingBiome(biomeName: String) {
        if (warnedBiomes.contains(biomeName)) return
        logger.warn("Biome $biomeName is not registered, skipping")
        warnedBiomes.add(biomeName)
    }


    override fun saveChunk(chunk: Chunk) {
        if (readOnly) return
        //TODO: Create slime chunk from chunk
//        chunkCache[getChunkIndex(chunk.chunkX, chunk.chunkZ)] = chunk
    }

    override fun saveInstance(instance: Instance) {
        if (readOnly) return

//        val outputStream = slimeSource.save()
//        val dataOutputStream = DataOutputStream(outputStream)

        //TODO: Update serializer
//        val serializer = SlimeSerializer()
//        serializer.serialize(dataOutputStream, instance, chunkCache.values.toList())

    }

    private fun loadRawData(dataStream: DataInputStream): ByteArray {
        val compressedData = ByteArray(dataStream.readInt())
        val uncompressedData = ByteArray(dataStream.readInt())
        dataStream.read(compressedData)
        Zstd.decompress(uncompressedData, compressedData)
        return uncompressedData
    }

}
