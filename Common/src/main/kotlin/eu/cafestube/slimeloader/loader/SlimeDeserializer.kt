package eu.cafestube.slimeloader.loader

import com.github.luben.zstd.Zstd
import eu.cafestube.slimeloader.UnknownFileTypeException
import eu.cafestube.slimeloader.UnsupportedSlimeVersionException
import eu.cafestube.slimeloader.data.*
import eu.cafestube.slimeloader.helpers.getChunkIndex
import eu.cafestube.slimeloader.helpers.readNBTTag
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.*
import kotlin.math.ceil

fun loadSlimeFile(dataStream: DataInputStream): SlimeFile {
    // Checking some magic numbers
    if (dataStream.readShort() != 0xB10B.toShort()) throw UnknownFileTypeException()

    return when(dataStream.readByte()) {
        0x09.toByte() -> loadSlimeFileV9(dataStream)
        10.toByte() -> loadSlimeFileV10(dataStream)
        11.toByte() -> loadSlimeFileV11(dataStream)
        12.toByte() -> loadSlimeFileV12(dataStream)
        13.toByte() -> loadSlimeFileV13(dataStream)

        else -> throw UnsupportedSlimeVersionException()
    }
}

fun loadSlimeFileV13(dataStream: DataInputStream): SlimeFile {
    val worldVersion = dataStream.readInt()
    val chunkFlags = dataStream.readByte()
    val chunksRaw = loadRawData(dataStream)
    val extraData = loadRawData(dataStream)

    val chunkFlagSet = EnumSet.noneOf(V13AdditionalWorldData::class.java).apply {
        for (data in V13AdditionalWorldData.entries) {
            if (data.isSet(chunkFlags)) {
                add(data)
            }
        }
    }

    // Closing the data stream
    dataStream.close()

    val extraTag = readNBTTag<CompoundBinaryTag>(extraData)
    val loader = SlimeChunkDeserializerV13(chunkFlagSet)

    val chunks = loader.readChunks(chunksRaw)

    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = chunkFlagSet,
        extraTag = extraTag,
        chunks = chunks
    )
}



fun loadSlimeFileV12(dataStream: DataInputStream): SlimeFile {
    val worldVersion = dataStream.readInt()
    val chunksRaw = loadRawData(dataStream)
    val extraData = loadRawData(dataStream)

    // Closing the data stream
    dataStream.close()

    val extraTag = readNBTTag<CompoundBinaryTag>(extraData)
    val loader = SlimeChunkDeserializerV12()

    val chunks = loader.readChunks(chunksRaw)
    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = EnumSet.noneOf(V13AdditionalWorldData::class.java),

        extraTag = extraTag,
        chunks = chunks
    )
}


fun loadSlimeFileV11(dataStream: DataInputStream): SlimeFile {
    val worldVersion = dataStream.readInt()
    val chunksRaw = loadRawData(dataStream)
    val extraData = loadRawData(dataStream)

    // Closing the data stream
    dataStream.close()

    val extraTag = readNBTTag<CompoundBinaryTag>(extraData)
    val loader = SlimeChunkDeserializerV11()

    val chunks = loader.readChunks(chunksRaw)

    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = EnumSet.noneOf(V13AdditionalWorldData::class.java),
        extraTag = extraTag,
        chunks = chunks
    )
}

fun loadSlimeFileV10(dataStream: DataInputStream): SlimeFile {
    val worldVersion = dataStream.readInt()
    val chunks = loadRawData(dataStream)
    val tileEntities = loadRawData(dataStream)
    val entityNBT = loadRawData(dataStream)
    val extraData = loadRawData(dataStream)

    // Closing the data stream
    dataStream.close()

    val extraTag = readNBTTag<CompoundBinaryTag>(extraData)

    val loader = SlimeChunkDeserializerV10()

    val chunkData = loader.readChunks(chunks)

    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = EnumSet.noneOf(V13AdditionalWorldData::class.java),

        extraTag = extraTag,
        chunks = chunkData
    )
}

fun loadSlimeFileV9(dataStream: DataInputStream): SlimeFile {
    val worldVersion = dataStream.readInt()

    val chunkMinX: Short = dataStream.readShort()
    val chunkMinZ: Short = dataStream.readShort()
    val width = dataStream.readUnsignedShort()
    val depth = dataStream.readUnsignedShort()

    // Chunks
    val chunkMaskSize = ceil((width * depth) / 8.0).toInt()
    val chunkMask = BitSet.valueOf(dataStream.readNBytes(chunkMaskSize))

    // Loading raw data
    val chunkData = loadRawData(dataStream)
    val tileEntitiesData = loadRawData(dataStream)
    if (dataStream.readBoolean()) loadRawData(dataStream) else ByteArray(0) // Skipping past entity data
    val extraData = loadRawData(dataStream)

    // Closing the data stream
    dataStream.close()


    val extraTag = readNBTTag<CompoundBinaryTag>(extraData)

    val loader = SlimeChunkDeserializerV9(tileEntitiesData, depth = depth, width = width, chunkMinX, chunkMinZ, chunkMask)

    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = EnumSet.noneOf(V13AdditionalWorldData::class.java),

        extraTag = extraTag,
        chunks = loader.readChunks(chunkData)
    )
}

class SlimeChunkDeserializerV13(val flags: EnumSet<V13AdditionalWorldData>) {

    private val arraySize = 16 * 16 * 16 / (8 / 4) // blocks / bytes per block

    fun readChunks(chunkData: ByteArray): MutableMap<Long, SlimeChunk> {
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))

        val size = chunkDataStream.readInt()
        val chunks = HashMap<Long, SlimeChunk>(size)

        for (i in 0 until size) {

            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val sections = readSections(chunkDataStream)

            val heightMapData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(heightMapData)
            val heightMapNBT = if (heightMapData.isNotEmpty()) readNBTTag(heightMapData) ?: CompoundBinaryTag.empty() else CompoundBinaryTag.empty()

            val tileEntityData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(tileEntityData)
            val tileEntities = if(tileEntityData.isNotEmpty()) readNBTTag(tileEntityData) ?: CompoundBinaryTag.empty() else CompoundBinaryTag.empty()

            val entityData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(entityData)
            val entityNBT = if(entityData.isNotEmpty()) readNBTTag(entityData) ?: CompoundBinaryTag.empty() else CompoundBinaryTag.empty()
            val extraData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(extraData)
            val extraNBT = if(extraData.isNotEmpty()) readNBTTag(extraData) ?: CompoundBinaryTag.empty() else CompoundBinaryTag.empty()


            val poiChunk: CompoundBinaryTag? = if(flags.contains(V13AdditionalWorldData.POI_CHUNKS)) {
                val poiData = ByteArray(chunkDataStream.readInt())
                chunkDataStream.read(poiData)

                if(poiData.isNotEmpty()) readNBTTag(poiData) ?: CompoundBinaryTag.empty() else CompoundBinaryTag.empty()
            } else null

            val blockTicks: ListBinaryTag? = if(flags.contains(V13AdditionalWorldData.BLOCK_TICKS)) {
                val blockTickData = ByteArray(chunkDataStream.readInt())
                chunkDataStream.read(blockTickData)

                if(blockTickData.isNotEmpty()) readNBTTag<CompoundBinaryTag>(blockTickData)
                    ?.getList("block_ticks") ?: ListBinaryTag.empty() else ListBinaryTag.empty()
            } else null

            val fluidTicks: ListBinaryTag? = if(flags.contains(V13AdditionalWorldData.FLUID_TICKS)) {
                val fluidTickData = ByteArray(chunkDataStream.readInt())
                chunkDataStream.read(fluidTickData)

                if(fluidTickData.isNotEmpty()) readNBTTag<CompoundBinaryTag>(fluidTickData)
                    ?.getList("fluid_ticks") ?: ListBinaryTag.empty() else ListBinaryTag.empty()
            } else null

            //TODO: Support unsupported flag compat

            val chunk = SlimeChunk(chunkX, chunkZ, sections, heightMapNBT, poiChunk, blockTicks,
                fluidTicks, tileEntities.getList("tileEntities"), entityNBT.getList("entities"), extraNBT)
            chunks[getChunkIndex(chunkX, chunkZ)] = chunk
        }

        return chunks
    }

    private fun readSections(chunkDataStream: DataInputStream): Array<SlimeSection> {
        val sections: Array<SlimeSection> = Array(chunkDataStream.readInt()) { DUMMY_SECTION }

        for(sectionId in sections.indices) {
            val sectionFlags = chunkDataStream.readByte().toInt()

            val blockLightArray: ByteArray? = if ((sectionFlags and 1) == 1) {
                ByteArray(arraySize).apply { chunkDataStream.read(this) }
            } else null
            val skyLightArray: ByteArray? = if (((sectionFlags shr 1) and 1) == 1) {
                ByteArray(arraySize).apply { chunkDataStream.read(this) }
            } else null

            val blockStateData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val blockStateTag = readNBTTag<CompoundBinaryTag>(blockStateData)!!

            val biomeData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val biomeTag = readNBTTag<CompoundBinaryTag>(biomeData)!!

            sections[sectionId] = SlimeSection(sectionId, blockStateTag, biomeTag, blockLightArray, skyLightArray)
        }

        return sections
    }

}

class SlimeChunkDeserializerV12 {

    private val arraySize = 16 * 16 * 16 / (8 / 4) // blocks / bytes per block

    fun readChunks(chunkData: ByteArray): MutableMap<Long, SlimeChunk> {
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))

        val size = chunkDataStream.readInt()
        val chunks = HashMap<Long, SlimeChunk>(size)

        for (i in 0 until size) {

            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val sections = readSections(chunkDataStream)

            val heightMapData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(heightMapData)
            val heightMapNBT = if (heightMapData.isNotEmpty()) readNBTTag(heightMapData) ?: CompoundBinaryTag.empty() else CompoundBinaryTag.empty()

            val tileEntityData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(tileEntityData)
            val tileEntities = if(tileEntityData.isNotEmpty()) readNBTTag(tileEntityData) ?: CompoundBinaryTag.empty() else CompoundBinaryTag.empty()

            val entityData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(entityData)
            val entityNBT = if(entityData.isNotEmpty()) readNBTTag(entityData) ?: CompoundBinaryTag.empty() else CompoundBinaryTag.empty()
            val extraData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(extraData)
            val extraNBT = if(extraData.isNotEmpty()) readNBTTag(extraData) ?: CompoundBinaryTag.empty() else CompoundBinaryTag.empty()

            val chunk = SlimeChunk(chunkX, chunkZ, sections, heightMapNBT, null,
                null, null, tileEntities.getList("tileEntities"), entityNBT.getList("entities"), extraNBT)
            chunks[getChunkIndex(chunkX, chunkZ)] = chunk
        }

        return chunks
    }

    private fun readSections(chunkDataStream: DataInputStream): Array<SlimeSection> {
        val sections: Array<SlimeSection> = Array(chunkDataStream.readInt()) { DUMMY_SECTION }

        for(sectionId in sections.indices) {
            val blockLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).apply { chunkDataStream.read(this) }
            } else null
            val skyLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).apply { chunkDataStream.read(this) }
            } else null

            val blockStateData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val blockStateTag = readNBTTag<CompoundBinaryTag>(blockStateData)!!

            val biomeData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val biomeTag = readNBTTag<CompoundBinaryTag>(biomeData)!!

            sections[sectionId] = SlimeSection(sectionId, blockStateTag, biomeTag, blockLightArray, skyLightArray)
        }

        return sections
    }

}

class SlimeChunkDeserializerV11 {

    private val arraySize = 16 * 16 * 16 / (8 / 4) // blocks / bytes per block

    fun readChunks(chunkData: ByteArray): MutableMap<Long, SlimeChunk> {
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))

        val size = chunkDataStream.readInt()
        val chunks = HashMap<Long, SlimeChunk>(size)

        for (i in 0 until size) {
            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val sections = readSections(chunkDataStream)

            val heightMapData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(heightMapData)
            val heightMapNBT = readNBTTag(heightMapData) ?: CompoundBinaryTag.empty()

            val tileEntities = readNBTTag<CompoundBinaryTag>(loadRawData(chunkDataStream)) ?: CompoundBinaryTag.empty()
            val entityNBT = readNBTTag<CompoundBinaryTag>(loadRawData(chunkDataStream)) ?: CompoundBinaryTag.empty()

            chunks[getChunkIndex(chunkX, chunkZ)] = SlimeChunk(chunkX, chunkZ, sections,
                heightMapNBT, null, null, null, tileEntities.getList("tileEntities"),
                entityNBT.getList("entities"), CompoundBinaryTag.empty())
        }

        return chunks
    }

    private fun readSections(chunkDataStream: DataInputStream): Array<SlimeSection> {
        val sections: Array<SlimeSection> = Array(chunkDataStream.readInt()) { DUMMY_SECTION }


        for(sectionId in sections.indices) {
            val blockLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).apply { chunkDataStream.read(this) }
            } else null
            val skyLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).apply { chunkDataStream.read(this) }
            } else null

            val blockStateData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val blockStateTag = readNBTTag<CompoundBinaryTag>(blockStateData)!!

            val biomeData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val biomeTag = readNBTTag<CompoundBinaryTag>(biomeData)!!

            sections[sectionId] = SlimeSection(sectionId, blockStateTag, biomeTag, blockLightArray, skyLightArray)
        }

        return sections
    }

}

private class SlimeChunkDeserializerV10 {


    val arraySize = 16 * 16 * 16 / (8 / 4) // blocks / bytes per block

    fun readChunks(chunkData: ByteArray): MutableMap<Long, SlimeChunk> {
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))

        val size = chunkDataStream.readInt()
        val chunks = HashMap<Long, SlimeChunk>(size)

        for (i in 0 until size) {
            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val heightMapData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(heightMapData)
            val heightMapNBT = readNBTTag(heightMapData) ?: CompoundBinaryTag.empty()


            val sections = readSections(chunkDataStream)

            //TODO: Support tile entities and entities
            chunks[getChunkIndex(chunkX, chunkZ)] = SlimeChunk(chunkX, chunkZ, sections,
                heightMapNBT, null, null, null,
                ListBinaryTag.empty(), ListBinaryTag.empty(), CompoundBinaryTag.empty())
        }

        chunkDataStream.close()

        return chunks
    }

    private fun readSections(chunkDataStream: DataInputStream): Array<SlimeSection> {
        val sections: Array<SlimeSection> = Array(chunkDataStream.readInt()) { DUMMY_SECTION }


        for(sectionId in sections.indices) {
            val blockLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).apply { chunkDataStream.read(this) }
            } else null
            val skyLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).apply { chunkDataStream.read(this) }
            } else null

            val blockStateData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val blockStateTag = readNBTTag<CompoundBinaryTag>(blockStateData)!!

            val biomeData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val biomeTag = readNBTTag<CompoundBinaryTag>(biomeData)!!

            sections[sectionId] = SlimeSection(sectionId, blockStateTag, biomeTag, blockLightArray, skyLightArray)
        }

        return sections
    }

}

private class SlimeChunkDeserializerV9(
    private val tileEntityData: ByteArray,
    private val depth: Int,
    private val width: Int,
    private val chunkMinX: Short,
    private val chunkMinZ: Short,
    private val chunkMask: BitSet,
) {

    fun readChunks(chunkData: ByteArray): MutableMap<Long, SlimeChunk> {
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))

        val tempChunks = mutableMapOf<Long, SlimeChunk>()
        for (chunkZ in 0 until depth) {
            for (chunkX in 0 until width) {
                val bitsetIndex = chunkZ * width + chunkX

                val realChunkX = chunkX + chunkMinX
                val realChunkZ = chunkZ + chunkMinZ

                if (chunkMask[bitsetIndex]) {
                    val chunk = readChunk(chunkDataStream, realChunkX, realChunkZ)
                    val chunkIndex = getChunkIndex(realChunkX, realChunkZ)
                    tempChunks[chunkIndex] = chunk
                }
            }
        }

//        loadTileEntities(tempChunks)
        chunkDataStream.close()
        return tempChunks
    }

    private fun readChunk(
        chunkDataStream: DataInputStream,
        chunkX: Int,
        chunkZ: Int,
    ): SlimeChunk {
        // Getting the heightmap
        val heightMapSize = chunkDataStream.readInt()
        val heightMap = ByteArray(heightMapSize)
        chunkDataStream.read(heightMap)
        val heightMapNBT = readNBTTag(heightMap) ?: CompoundBinaryTag.empty()

        val readChunkSections = readChunkSections(chunkDataStream)

        return SlimeChunk(chunkX, chunkZ, readChunkSections, heightMapNBT, null, null, null,
            ListBinaryTag.empty(), ListBinaryTag.empty(), CompoundBinaryTag.empty())
    }

    private fun readChunkSections(dataStream: DataInputStream): Array<SlimeSection> {
        dataStream.readInt() // - minSectionY skipping
        dataStream.readInt() // - maxSectionY skipping
        val sectionCount = dataStream.readInt()

        val sections = Array(sectionCount) { DUMMY_SECTION }

        for (chunkSection in 0 until sectionCount) {
            dataStream.readInt() //ChunkY - skip

            var blockLightArray: ByteArray? = null
            if (dataStream.readBoolean()) {
                blockLightArray = ByteArray(2048)
                dataStream.read(blockLightArray)
            }

            val blockStateData = ByteArray(dataStream.readInt())
            dataStream.read(blockStateData)
            val blockStateTag = readNBTTag<CompoundBinaryTag>(blockStateData)!!

            val biomeData = ByteArray(dataStream.readInt())
            dataStream.read(biomeData)
            val biomeTag = readNBTTag<CompoundBinaryTag>(biomeData)!!

            var skyLightArray: ByteArray? = null
            if (dataStream.readBoolean()) {
                skyLightArray = ByteArray(2048)
                dataStream.read(skyLightArray)
            }

            val section = SlimeSection(chunkSection, blockStateTag, biomeTag, blockLightArray, skyLightArray)
            sections[chunkSection] = section
        }

        return sections
    }


//    private fun loadTileEntities(chunks: Map<Long, Chunk>) {
//        val tileEntitiesCompound = readNBTTag<NBTCompound>(tileEntityData) ?: return
//        val tileEntities = tileEntitiesCompound.getList<NBTCompound>("tiles") ?: return
//        for (tileEntity in tileEntities) {
//            val x = tileEntity.getInt("x") ?: continue
//            val y = tileEntity.getInt("y") ?: continue
//            val z = tileEntity.getInt("z") ?: continue
//
//            val localX = x % 16 + (if(x < 0) 16 else 0)
//            val localZ = z % 16 + (if(z < 0) 16 else 0)
//
//            val chunkX = floor(x / 16.0).toInt()
//            val chunkZ = floor(z / 16.0).toInt()
//
//            val chunk = chunks[ChunkHelpers.getChunkIndex(chunkX, chunkZ)] ?: continue
//            var block = chunk.getBlock(localX, y, localZ)
//
//            val id: String? = tileEntity.getString("id")
//            if (id != null) {
//                val blockHandler = MinecraftServer.getBlockManager().getHandler(id)
//                if (blockHandler != null) {
//                    block = block.withHandler(blockHandler)
//                }
//            }
//
//            val compactedTileEntity = tileEntity.withRemovedKeys("x", "y", "z", "id", "keepPacked")
//            if (compactedTileEntity.size > 0) {
//                block = block.withNbt(compactedTileEntity)
//            }
//
//            chunk.setBlock(localX, y, localZ, block)
//        }
//    }

}

private fun loadRawData(dataStream: DataInputStream): ByteArray {
    val compressedData = ByteArray(dataStream.readInt())
    val uncompressedData = ByteArray(dataStream.readInt())
    dataStream.read(compressedData)
    Zstd.decompress(uncompressedData, compressedData)
    return uncompressedData
}