package gg.astromc.slimeloader.loader

import eu.cafestube.slimeloader.data.DUMMY_SECTION
import eu.cafestube.slimeloader.data.SlimeChunk
import eu.cafestube.slimeloader.data.SlimeSection
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.BinaryTagTypes
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag
import net.kyori.adventure.nbt.LongArrayBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.MinestomAdventure
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Section
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.palette.Palettes
import net.minestom.server.registry.RegistryKey
import net.minestom.server.utils.MathUtils
import net.minestom.server.world.biome.Biome
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.HashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max


private val defaultBiome: Int = MinecraftServer.getBiomeRegistry().getId(Biome.PLAINS)
private val blockStateId2NBTCache: Int2ObjectMap<CompoundBinaryTag> = Int2ObjectArrayMap();
private val logger = LoggerFactory.getLogger(SlimeLoader::class.java)

@Suppress("UnstableApiUsage")
fun loadBiomePalette(paletteTag: ListBinaryTag): IntArray {
    val convertedPalette = IntArray(paletteTag.size())

    paletteTag.forEachIndexed { index, tag ->
        if (tag !is StringBinaryTag) {
            throw IllegalStateException("Expected a StringBinaryTag in biome palette at index $index, but got ${tag.examinableName()}")
        }

        val biomeId = MinecraftServer.getBiomeRegistry().getId(RegistryKey.unsafeOf(tag.value()))
        convertedPalette[index] = if(biomeId == -1) defaultBiome else biomeId
    }
    return convertedPalette
}

fun loadBlockPalette(paletteTag: ListBinaryTag): Array<Block> {
    val convertedPalette = Array(paletteTag.size()) { Block.AIR }

    paletteTag.forEachIndexed { index, tag ->
        if (tag !is CompoundBinaryTag) {
            throw IllegalStateException("Expected a CompoundBinaryTag in block palette at index $index, but got ${tag.examinableName()}")
        }
        val name = tag.getString("Name")

        if (name == "minecraft:air") {
            convertedPalette[index] = Block.AIR
            return@forEachIndexed
        }
        var block = Block.fromKey(name) ?: throw IllegalStateException("Unknown block $name")
        val properties = parseBlockProperties(tag.getCompound("Properties"))

        if(properties.isNotEmpty()) {
            block = block.withProperties(properties)
        }
        val handler = MinecraftServer.getBlockManager().getHandler(block.name())
        if (handler != null) block = block.withHandler(handler)

        convertedPalette[index] = block
    }

    return convertedPalette
}

private fun parseBlockProperties(compound: CompoundBinaryTag): Map<String, String> {
    if(compound.isEmpty) return emptyMap()

    val properties = HashMap<String, String>()
    compound.forEach { (key, value) ->
        if(value is StringBinaryTag) {
            properties[key] = value.value()
        } else {
            logger.warn("Failed to parse block state. Got: {}", MinestomAdventure.tagStringIO().asString(value))
        }
    }
    return properties
}

@Suppress("UnstableApiUsage")
fun applyChunkSection(chunk: Chunk, section: Section, slimeSection: SlimeSection) {
    if (slimeSection.skyLight != null)
        section.setSkyLight(slimeSection.skyLight)
    if (slimeSection.blockLight != null) {
        section.setBlockLight(slimeSection.blockLight)
    }

    val biome = loadBiomePalette(slimeSection.biomeTag.getList("palette"))

    if (biome.size == 1) {
        section.biomePalette().fill(biome.first())
    } else if(biome.size > 1) {
        val packedIndices = slimeSection.biomeTag.getLongArray("data");
        if(packedIndices.isEmpty())
            throw IllegalStateException("Missing packed biomes data in slime section ${slimeSection.index} at chunk ${chunk.chunkX}, ${chunk.chunkZ}")


        val biomeIndices = IntArray(64)

        var bitsPerEntry = packedIndices.size * 64 / biomeIndices.size
        if (bitsPerEntry > 3)
            bitsPerEntry = MathUtils.bitsToRepresent(biome.size)
        Palettes.unpack(biomeIndices, packedIndices, bitsPerEntry)

        section.biomePalette().setAll { x: Int, y: Int, z: Int ->
            val index = x + z * 4 + y * 16
            biome[biomeIndices[index]]
        }
    }



    val dimensionType = MinecraftServer.getDimensionTypeRegistry().get(chunk.instance.dimensionType)!!

    val blocks = slimeSection.blockStateTag
    val blockPalette = blocks.getList("palette")
    val palette = loadBlockPalette(blockPalette)

    if (palette.size == 1) {
        // One solid block, no need to check the data
        section.blockPalette().fill(palette.first().stateId())
    } else if (palette.size > 1) {
        val packedStates: LongArray = blocks.getLongArray("data")
        if(packedStates.isEmpty())
            throw IllegalStateException("Missing packed states data in slime section ${slimeSection.index} at chunk ${chunk.chunkX}, ${chunk.chunkZ}")

        val blockStateIndices = IntArray(Chunk.CHUNK_SECTION_SIZE * Chunk.CHUNK_SECTION_SIZE * Chunk.CHUNK_SECTION_SIZE)
        Palettes.unpack(blockStateIndices, packedStates, packedStates.size * 64 / blockStateIndices.size)

        for (y in 0..<Chunk.CHUNK_SECTION_SIZE) {
            for (z in 0..<Chunk.CHUNK_SECTION_SIZE) {
                for (x in 0..<Chunk.CHUNK_SECTION_SIZE) {
                    try {
                        val blockIndex = y * Chunk.CHUNK_SECTION_SIZE * Chunk.CHUNK_SECTION_SIZE + z * Chunk.CHUNK_SECTION_SIZE + x
                        val paletteIndex = blockStateIndices[blockIndex]
                        val block: Block = palette[paletteIndex]

                        chunk.setBlock(x, dimensionType.minY() + y + (Chunk.CHUNK_SECTION_SIZE * slimeSection.index), z, block)
                    } catch (e: Exception) {
                        MinecraftServer.getExceptionManager().handleException(e)
                    }
                }
            }
        }
    }

}

/*
 * Contains code from Minestom's AnvilLoader licenced under Apache License 2.0
 */
fun applyBlockEntities(chunk: Chunk, slimeChunk: SlimeChunk) {
    for (blockEntityTag in slimeChunk.tileEntities) {
        val blockEntity = blockEntityTag as CompoundBinaryTag

        val x = blockEntity.getInt("x")
        val y = blockEntity.getInt("y")
        val z = blockEntity.getInt("z")
        var block = chunk.getBlock(x, y, z)

        val blockEntityId = blockEntity.get("id")
        if (blockEntityId is StringBinaryTag) {
            val handler = MinecraftServer.getBlockManager().getHandlerOrDummy(blockEntityId.value())
            block = block.withHandler(handler)
        }

        // Remove vanilla tags
        val trimmedTag = CompoundBinaryTag.builder().put(blockEntity)
            .remove("id")
            .remove("keepPacked")
            .remove("x")
            .remove("y")
            .remove("z")
            .build()


        val finalBlock = if (trimmedTag.size() > 0) block.withNbt(trimmedTag) else block
        chunk.setBlock(x, y, z, finalBlock)
    }
}

@Suppress("UnstableApiUsage")
/*
 * Contains code from Minestom's AnvilLoader licenced under Apache License 2.0
 */
fun toSlimeChunk(chunk: Chunk): SlimeChunk {
    val sections = Array(chunk.sections.size) { DUMMY_SECTION }
    val blockEntities = ListBinaryTag.builder(BinaryTagTypes.COMPOUND)

    // Block & Biome arrays reused for each chunk section
    val biomePalette: MutableList<BinaryTag> = ArrayList<BinaryTag>()
    val biomeIndices = IntArray(64)

    val blockPaletteEntries: MutableList<BinaryTag> = ArrayList<BinaryTag>()
    val blockPaletteIndices: IntList = IntArrayList() // Map block indices by state id to avoid doing a deep comparison on every block tag
    val blockIndices = IntArray(Chunk.CHUNK_SECTION_SIZE * Chunk.CHUNK_SECTION_SIZE * Chunk.CHUNK_SECTION_SIZE)


    synchronized(chunk) {
        for (sectionY in chunk.getMinSection()..<chunk.getMaxSection()) {
            val index = sectionY-chunk.minSection
            val section = chunk.getSection(sectionY)

            val skyLight = section.skyLight().array()?.takeIf { it.isNotEmpty() }
            val blockLight = section.blockLight().array()?.takeIf { it.isNotEmpty() }

            for (y in 0..<Chunk.CHUNK_SECTION_SIZE) {
                for (z in 0..<Chunk.CHUNK_SIZE_Z) {
                    for (x in 0..<Chunk.CHUNK_SIZE_X) {
                        val worldY = y + (sectionY * Chunk.CHUNK_SECTION_SIZE)

                        val blockIndex = x + y * 16 * 16 + z * 16
                        val block = chunk.getBlock(x, worldY, z)

                        // Add block state
                        val blockStateId = block.stateId()
                        val blockState: CompoundBinaryTag = getBlockState(block)
                        var blockPaletteIndex = blockPaletteIndices.indexOf(blockStateId)
                        if (blockPaletteIndex == -1) {
                            blockPaletteIndex = blockPaletteEntries.size
                            blockPaletteEntries.add(blockState)
                            blockPaletteIndices.add(blockStateId)
                        }
                        blockIndices[blockIndex] = blockPaletteIndex

                        // Add biome (biome are stored for 4x4x4 volumes, avoid unnecessary work)
                        if (x % 4 == 0 && y % 4 == 0 && z % 4 == 0) {
                            val biomeIndex = (x / 4) + (y / 4) * 4 * 4 + (z / 4) * 4
                            val biomeKey = chunk.getBiome(x, worldY, z)
                            val biomeName: BinaryTag = StringBinaryTag.stringBinaryTag(biomeKey.key().asString())

                            var biomePaletteIndex = biomePalette.indexOf(biomeName)
                            if (biomePaletteIndex == -1) {
                                biomePaletteIndex = biomePalette.size
                                biomePalette.add(biomeName)
                            }

                            biomeIndices[biomeIndex] = biomePaletteIndex
                        }

                        // Add block entity if present
                        val handler = block.handler()
                        val originalNBT = block.nbt()
                        if (originalNBT != null || handler != null) {
                            val blockEntityTag = CompoundBinaryTag.builder()
                            if (originalNBT != null) {
                                blockEntityTag.put(originalNBT)
                            }
                            if (handler != null) {
                                blockEntityTag.putString("id", handler.getKey().asString())
                            }
                            blockEntityTag.putInt("x", x + Chunk.CHUNK_SIZE_X * chunk.getChunkX())
                            blockEntityTag.putInt("y", worldY)
                            blockEntityTag.putInt("z", z + Chunk.CHUNK_SIZE_Z * chunk.getChunkZ())
                            blockEntityTag.putByte("keepPacked", 0.toByte())
                            blockEntities.add(blockEntityTag.build())
                        }
                    }
                }
            }

            // Save the block and biome palettes
            val blockStates = CompoundBinaryTag.builder()
            blockStates.put("palette", ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, blockPaletteEntries))
            if (blockPaletteEntries.size > 1) {
                // If there is only one entry we do not need to write the packed indices
                val bitsPerEntry = max(4.0, ceil(ln(blockPaletteEntries.size.toDouble()) / ln(2.0))).toInt()
                blockStates.putLongArray("data", Palettes.pack(blockIndices, bitsPerEntry))
            }

            val biomes = CompoundBinaryTag.builder()
            biomes.put("palette", ListBinaryTag.listBinaryTag(BinaryTagTypes.STRING, biomePalette))
            if (biomePalette.size > 1) {
                // If there is only one entry we do not need to write the packed indices
                val bitsPerEntry = max(1.0, ceil(ln(biomePalette.size.toDouble()) / ln(2.0))).toInt()
                biomes.putLongArray("data", Palettes.pack(biomeIndices, bitsPerEntry))
            }

            biomePalette.clear()
            blockPaletteEntries.clear()
            blockPaletteIndices.clear()

            sections[index] = SlimeSection(
                index = index,
                blockStateTag = blockStates.build(),
                biomeTag = biomes.build(),
                blockLight = blockLight,
                skyLight = skyLight,
            )
        }
    }

    val heightmaps = CompoundBinaryTag.builder()
        .put(chunk.motionBlockingHeightmap().type().name, LongArrayBinaryTag.longArrayBinaryTag(*chunk.motionBlockingHeightmap().nbt))
        .put(chunk.worldSurfaceHeightmap().type().name, LongArrayBinaryTag.longArrayBinaryTag(*chunk.worldSurfaceHeightmap().nbt))
        .build()

    return SlimeChunk(
        chunk.chunkX,
        chunk.chunkZ,
        sections,
        heightMaps = heightmaps,
        poiChunk = null,
        blockTicks = null,
        fluidTicks = null,
        tileEntities = blockEntities.build(),
        entities = ListBinaryTag.empty(),
        extra = CompoundBinaryTag.empty()
    )
}

/*
* Contains code from Minestom's AnvilLoader licenced under Apache License 2.0
*/
private fun getBlockState(block: Block): CompoundBinaryTag {
    return blockStateId2NBTCache.computeIfAbsent(block.stateId(), Int2ObjectFunction { _unused: Int ->
        val tag = CompoundBinaryTag.builder()
        tag.putString("Name", block.name())

        if (!block.properties().isEmpty()) {
            val defaultProperties = Block.fromBlockId(block.id())!!.properties() // Never null
            val propertiesTag = CompoundBinaryTag.builder()
            for (entry in block.properties().entries) {
                val key = entry.key
                val value = entry.value
                if (defaultProperties[key] == value) continue  // Skip default values

                propertiesTag.putString(key, value)
            }
            val properties = propertiesTag.build()
            if (properties.size() > 0) {
                tag.put("Properties", properties)
            }
        }
        tag.build()
    })
}
