package eu.cafestube.slimeloader.loader

import com.github.luben.zstd.Zstd
import eu.cafestube.slimeloader.UnknownFileTypeException
import eu.cafestube.slimeloader.UnsupportedMinecraftVersionException
import eu.cafestube.slimeloader.UnsupportedSlimeVersionException
import eu.cafestube.slimeloader.data.*
import eu.cafestube.slimeloader.helpers.ChunkHelpers
import eu.cafestube.slimeloader.helpers.NBTHelpers
import org.jglrxavpok.hephaistos.nbt.NBTCompound
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

        else -> throw UnsupportedSlimeVersionException()
    }
}

fun loadSlimeFileV12(dataStream: DataInputStream): SlimeFile {
    val worldVersion = dataStream.readInt()
    val chunksRaw = loadRawData(dataStream)
    val extraData = loadRawData(dataStream)

    // Closing the data stream
    dataStream.close()

    val extraTag = NBTHelpers.readNBTTag<NBTCompound>(extraData)
    val loader = SlimeChunkDeserializerV12()

    val chunks = loader.readChunks(chunksRaw)

    val minX = chunks.entries.minOf { it.value.x }
    val minZ = chunks.entries.minOf { it.value.z }

    val maxX = chunks.entries.maxOf { it.value.x }
    val maxZ = chunks.entries.maxOf { it.value.z }

    return SlimeFile(
        worldVersion = worldVersion,
        chunkMinX = minX.toShort(),
        chunkMinZ = minZ.toShort(),
        width = maxX - minX,
        depth = maxZ - minZ,
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

    val extraTag = NBTHelpers.readNBTTag<NBTCompound>(extraData)
    val loader = SlimeChunkDeserializerV11()

    val chunks = loader.readChunks(chunksRaw)

    val minX = chunks.entries.minOf { it.value.x }
    val minZ = chunks.entries.minOf { it.value.z }

    val maxX = chunks.entries.maxOf { it.value.x }
    val maxZ = chunks.entries.maxOf { it.value.z }

    return SlimeFile(
        worldVersion = worldVersion,
        chunkMinX = minX.toShort(),
        chunkMinZ = minZ.toShort(),
        width = maxX - minX,
        depth = maxZ - minZ,
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

    val extraTag = NBTHelpers.readNBTTag<NBTCompound>(extraData)

    val loader = SlimeChunkDeserializerV10()

    val chunkData = loader.readChunks(chunks)

    val minX = chunkData.entries.minOf { it.value.x }
    val minZ = chunkData.entries.minOf { it.value.z }

    val maxX = chunkData.entries.maxOf { it.value.x }
    val maxZ = chunkData.entries.maxOf { it.value.z }

    return SlimeFile(
        worldVersion = worldVersion,
        chunkMinX = minX.toShort(),
        chunkMinZ = minZ.toShort(),
        width = maxX - minX,
        depth = maxZ - minZ,
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


    val extraTag = NBTHelpers.readNBTTag<NBTCompound>(extraData)

    val loader = SlimeChunkDeserializerV9(tileEntitiesData, depth = depth, width = width, chunkMinX, chunkMinZ, chunkMask)

    return SlimeFile(
        worldVersion = worldVersion,
        chunkMinX = chunkMinX,
        chunkMinZ = chunkMinZ,
        width = width,
        depth = depth,
        extraTag = extraTag,
        chunks = loader.readChunks(chunkData)
    )
}

class SlimeChunkDeserializerV12 {

    private val arraySize = 16 * 16 * 16 / (8 / 4) // blocks / bytes per block

    fun readChunks(chunkData: ByteArray): Map<Long, SlimeChunk> {
        val chunks = mutableListOf<SlimeChunk>()
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))

        val size = chunkDataStream.readInt()

        for (i in 0 until size) {
            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val sections = readSections(chunkDataStream)

            val heightMapData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(heightMapData)
            val heightMapNBT = NBTHelpers.readNBTTag(heightMapData) ?: NBTCompound()

            val tileEntityData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(tileEntityData)
            val tileEntities = NBTHelpers.readNBTTag(tileEntityData) ?: NBTCompound()

            val entityData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(entityData)
            val entityNBT = NBTHelpers.readNBTTag(entityData) ?: NBTCompound()

            val extraData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(extraData)
            val extraNBT = NBTHelpers.readNBTTag(extraData) ?: NBTCompound()

            chunks.add(SlimeChunk(chunkX, chunkZ, sections, heightMapNBT, tileEntities, entityNBT, extraNBT))
        }

        return chunks.associateBy { ChunkHelpers.getChunkIndex(it.x, it.z) }
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
            val blockStateTag = NBTHelpers.readNBTTag<NBTCompound>(blockStateData)!!

            val biomeData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val biomeTag = NBTHelpers.readNBTTag<NBTCompound>(biomeData)!!

            sections[sectionId] = SlimeSection(sectionId, blockStateTag, biomeTag, blockLightArray, skyLightArray)
        }

        return sections
    }

}

class SlimeChunkDeserializerV11 {

    private val arraySize = 16 * 16 * 16 / (8 / 4) // blocks / bytes per block

    fun readChunks(chunkData: ByteArray): Map<Long, SlimeChunk> {
        val chunks = mutableListOf<SlimeChunk>()
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))

        val size = chunkDataStream.readInt()

        for (i in 0 until size) {
            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val sections = readSections(chunkDataStream)

            val heightMapData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(heightMapData)
            val heightMapNBT = NBTHelpers.readNBTTag(heightMapData) ?: NBTCompound()

            val tileEntities = NBTHelpers.readNBTTag<NBTCompound>(loadRawData(chunkDataStream)) ?: NBTCompound()
            val entityNBT = NBTHelpers.readNBTTag<NBTCompound>(loadRawData(chunkDataStream)) ?: NBTCompound()

            chunks.add(SlimeChunk(chunkX, chunkZ, sections, heightMapNBT, tileEntities, entityNBT, NBTCompound()))
        }

        return chunks.associateBy { ChunkHelpers.getChunkIndex(it.x, it.z) }
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
            val blockStateTag = NBTHelpers.readNBTTag<NBTCompound>(blockStateData)!!

            val biomeData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val biomeTag = NBTHelpers.readNBTTag<NBTCompound>(biomeData)!!

            sections[sectionId] = SlimeSection(sectionId, blockStateTag, biomeTag, blockLightArray, skyLightArray)
        }

        return sections
    }

}

private class SlimeChunkDeserializerV10 {


    val arraySize = 16 * 16 * 16 / (8 / 4) // blocks / bytes per block

    fun readChunks(chunkData: ByteArray): Map<Long, SlimeChunk> {
        val chunks = mutableListOf<SlimeChunk>()
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))

        val size = chunkDataStream.readInt()

        for (i in 0 until size) {
            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val heightMapData = ByteArray(chunkDataStream.readInt())
            chunkDataStream.read(heightMapData)
            val heightMapNBT = NBTHelpers.readNBTTag(heightMapData) ?: NBTCompound()


            val sections = readSections(chunkDataStream)

            chunks.add(SlimeChunk(chunkX, chunkZ, sections, heightMapNBT, NBTCompound(), NBTCompound(), NBTCompound()))
        }

        chunkDataStream.close()

        return chunks.associateBy { ChunkHelpers.getChunkIndex(it.x, it.z) }
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
            val blockStateTag = NBTHelpers.readNBTTag<NBTCompound>(blockStateData)!!

            val biomeData = ByteArray(chunkDataStream.readInt()).apply { chunkDataStream.read(this) }
            val biomeTag = NBTHelpers.readNBTTag<NBTCompound>(biomeData)!!

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

    fun readChunks(chunkData: ByteArray): Map<Long, SlimeChunk> {
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))

        val tempChunks = mutableMapOf<Long, SlimeChunk>()
        for (chunkZ in 0 until depth) {
            for (chunkX in 0 until width) {
                val bitsetIndex = chunkZ * width + chunkX

                val realChunkX = chunkX + chunkMinX
                val realChunkZ = chunkZ + chunkMinZ

                if (chunkMask[bitsetIndex]) {
                    val chunk = readChunk(chunkDataStream, realChunkX, realChunkZ)
                    val chunkIndex = ChunkHelpers.getChunkIndex(realChunkX, realChunkZ)
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
        val heightMapNBT = NBTHelpers.readNBTTag(heightMap) ?: NBTCompound()

        val readChunkSections = readChunkSections(chunkDataStream)

        return SlimeChunk(chunkX, chunkZ, readChunkSections, heightMapNBT, NBTCompound(), NBTCompound(), NBTCompound())
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
            val blockStateTag = NBTHelpers.readNBTTag<NBTCompound>(blockStateData)!!

            val biomeData = ByteArray(dataStream.readInt())
            dataStream.read(biomeData)
            val biomeTag = NBTHelpers.readNBTTag<NBTCompound>(biomeData)!!

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
//        val tileEntitiesCompound = NBTHelpers.readNBTTag<NBTCompound>(tileEntityData) ?: return
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