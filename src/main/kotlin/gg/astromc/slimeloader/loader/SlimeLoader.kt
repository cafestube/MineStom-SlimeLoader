package gg.astromc.slimeloader.loader

import com.github.luben.zstd.Zstd
import eu.cafestube.slimeloader.helpers.ChunkHelpers.getChunkIndex
import eu.cafestube.slimeloader.helpers.NBTHelpers
import eu.cafestube.slimeloader.loader.loadSlimeFile
import gg.astromc.slimeloader.data.NoOpSlimeFixer
import gg.astromc.slimeloader.data.SlimeDataFixer
import gg.astromc.slimeloader.source.SlimeSource
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.chunk.ChunkUtils.*
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTString
import org.jglrxavpok.hephaistos.nbt.NBTType
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

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): CompletableFuture<Chunk?> {
        val slimeChunk =
            slimeFile.chunks[getChunkIndex(chunkX, chunkZ)] ?: return CompletableFuture.completedFuture(null)

        val chunk = instance.chunkSupplier.createChunk(instance, chunkX, chunkZ)
        slimeChunk.sections.forEach { slimeSection ->
            val section = chunk.sections[slimeSection.index]
            if (slimeSection.skyLight != null)
                section.setSkyLight(slimeSection.skyLight)
            if (slimeSection.blockLight != null) {
                section.setBlockLight(slimeSection.blockLight)
            }
            val biomes = NBTHelpers.readBiomes(slimeSection.biomeTag)

            if (biomes.hasBiomeInformation()) {
                if (biomes.isFilledWithSingleBiome()) {
                    val biome = MinecraftServer.getBiomeManager().getByName(NamespaceID.from(biomes.baseBiome!!))
                    if (biome != null) {
                        for (y in 0 until Chunk.CHUNK_SECTION_SIZE) {
                            for (z in 0 until Chunk.CHUNK_SIZE_Z) {
                                for (x in 0 until Chunk.CHUNK_SIZE_X) {
                                    val finalX = chunk.chunkX * Chunk.CHUNK_SIZE_X + x
                                    val finalZ = chunk.chunkZ * Chunk.CHUNK_SIZE_Z + z

                                    @Suppress("UnstableApiUsage")
                                    section.biomePalette().set(toSectionRelativeCoordinate(finalX) / 4,
                                        y, toSectionRelativeCoordinate(finalZ) / 4, biome.id()
                                    )
                                }
                            }
                        }
                    } else {
                        warnAboutMissingBiome(biomes.baseBiome!!)
                    }

                } else {
                    for (y in 0 until Chunk.CHUNK_SECTION_SIZE) {
                        for (z in 0 until Chunk.CHUNK_SIZE_Z) {
                            for (x in 0 until Chunk.CHUNK_SIZE_X) {
                                val finalX = chunk.chunkX * Chunk.CHUNK_SIZE_X + x
                                val finalZ = chunk.chunkZ * Chunk.CHUNK_SIZE_Z + z
                                val finalY: Int = slimeSection.index * Chunk.CHUNK_SECTION_SIZE + y
                                val index = x / 4 + z / 4 * 4 + y / 4 * 16
                                val biomeName: String = biomes.biomes!![index]
                                val biome = MinecraftServer.getBiomeManager().getByName(NamespaceID.from(biomeName))
                                if (biome == null) {
                                    warnAboutMissingBiome(biomeName)
                                    continue
                                }

                                @Suppress("UnstableApiUsage")
                                section.biomePalette().set(toSectionRelativeCoordinate(finalX) / 4,
                                    y, toSectionRelativeCoordinate(finalZ) / 4, biome.id()
                                )
                            }
                        }
                    }
                }
            }

            //Blocks
            val blocks = slimeSection.blockStateTag
            val blockPalette = blocks.getList<NBTCompound>("palette")
            val blockData = NBTHelpers.loadBlockData(blocks)

            val convertedPalette = arrayOfNulls<Block>(blockPalette!!.size)
            for (i in convertedPalette.indices) {
                val paletteEntry = blockPalette[i]
                val blockName = paletteEntry.getString("Name")!!
                if (blockName == "minecraft:air") {
                    convertedPalette[i] = Block.AIR
                    continue
                } else {
                    val properties = HashMap<String, String>()
                    val propertiesNbt = paletteEntry.getCompound("Properties")
                    if (propertiesNbt != null) {
                        for ((key, value) in propertiesNbt) {
                            if (value.ID != NBTType.TAG_String) {
                                logger.warn(
                                    "Fail to parse block state properties {}, expected a TAG_String for {}, but contents were {}",
                                    propertiesNbt, key, value.toSNBT()
                                )
                            } else {
                                properties[key] = (value as NBTString).value
                            }
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
                                    instance.dimensionType.minY + y + (Chunk.CHUNK_SECTION_SIZE * slimeSection.index),
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

        return CompletableFuture.completedFuture(chunk)
    }


    private fun warnAboutMissingBiome(biomeName: String) {
        if (warnedBiomes.contains(biomeName)) return
        logger.warn("Biome $biomeName is not registered, skipping")
        warnedBiomes.add(biomeName)
    }


    override fun saveChunk(chunk: Chunk): CompletableFuture<Void> {
        if (readOnly) return CompletableFuture.completedFuture(null)
        //TODO: Create slime chunk from chunk
//        chunkCache[getChunkIndex(chunk.chunkX, chunk.chunkZ)] = chunk
        return CompletableFuture.completedFuture(null)
    }

    override fun saveInstance(instance: Instance): CompletableFuture<Void> {
        if (readOnly) return CompletableFuture.completedFuture(null)

//        val outputStream = slimeSource.save()
//        val dataOutputStream = DataOutputStream(outputStream)

        //TODO: Update serializer
//        val serializer = SlimeSerializer()
//        serializer.serialize(dataOutputStream, instance, chunkCache.values.toList())

        return CompletableFuture.completedFuture(null)
    }

    private fun loadRawData(dataStream: DataInputStream): ByteArray {
        val compressedData = ByteArray(dataStream.readInt())
        val uncompressedData = ByteArray(dataStream.readInt())
        dataStream.read(compressedData)
        Zstd.decompress(uncompressedData, compressedData)
        return uncompressedData
    }

}
