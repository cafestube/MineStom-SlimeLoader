package eu.cafestube.slimeloader.loader

import com.github.luben.zstd.Zstd
import eu.cafestube.slimeloader.UnknownFileTypeException
import eu.cafestube.slimeloader.UnsupportedSlimeVersionException
import eu.cafestube.slimeloader.data.*
import eu.cafestube.slimeloader.helpers.getChunkIndex
import eu.cafestube.slimeloader.helpers.openCompressedStream
import eu.cafestube.slimeloader.helpers.readCompressedCompound
import eu.cafestube.slimeloader.helpers.readLimitedCompound
import net.kyori.adventure.nbt.BinaryTagTypes
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag
import java.io.DataInputStream
import java.util.*
import kotlin.math.ceil

fun loadSlimeFile(dataStream: DataInputStream): SlimeFile {
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

fun loadSlimeFileV13(dataStream: DataInputStream): SlimeFile = dataStream.use {
    val worldVersion = dataStream.readInt()
    val chunkFlags = dataStream.readByte()

    val chunkFlagSet = EnumSet.noneOf(V13AdditionalWorldData::class.java).apply {
        for (data in V13AdditionalWorldData.entries) {
            if (data.isSet(chunkFlags)) {
                add(data)
            }
        }
    }

    val loader = SlimeChunkDeserializerV13(chunkFlagSet)
    val chunks = openCompressedStream(dataStream).use {
        loader.readChunks(it)
    }
    val extraTag = readCompressedCompound(dataStream)

    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = chunkFlagSet,
        extraTag = extraTag,
        chunks = chunks
    )
}

fun loadSlimeFileV12(dataStream: DataInputStream): SlimeFile = dataStream.use {
    val worldVersion = dataStream.readInt()
    val loader = SlimeChunkDeserializerV12()
    val chunks = openCompressedStream(dataStream).use {
        loader.readChunks(it)
    }
    val extraTag = readCompressedCompound(dataStream)

    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = EnumSet.noneOf(V13AdditionalWorldData::class.java),
        extraTag = extraTag,
        chunks = chunks
    )
}

fun loadSlimeFileV11(dataStream: DataInputStream): SlimeFile = dataStream.use {
    val worldVersion = dataStream.readInt()
    val loader = SlimeChunkDeserializerV11()
    val chunks = openCompressedStream(dataStream).use {
        loader.readChunks(it)
    }
    val extraTag = readCompressedCompound(dataStream)


    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = EnumSet.noneOf(V13AdditionalWorldData::class.java),
        extraTag = extraTag,
        chunks = chunks
    )
}

fun loadSlimeFileV10(dataStream: DataInputStream): SlimeFile = dataStream.use {
    val worldVersion = dataStream.readInt()
    val loader = SlimeChunkDeserializerV10()
    val chunks = openCompressedStream(dataStream).use {
        loader.readChunks(it)
    }
    val tileEntities = loadRawData(dataStream) //TODO:
    val entityNBT = loadRawData(dataStream) //TODO:
    val extraTag = readCompressedCompound(dataStream)


    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = EnumSet.noneOf(V13AdditionalWorldData::class.java),
        extraTag = extraTag,
        chunks = chunks
    )
}

fun loadSlimeFileV9(dataStream: DataInputStream): SlimeFile {
    val worldVersion = dataStream.readInt()

    val chunkMinX: Short = dataStream.readShort()
    val chunkMinZ: Short = dataStream.readShort()
    val width = dataStream.readUnsignedShort()
    val depth = dataStream.readUnsignedShort()

    val chunkMaskSize = ceil((width * depth) / 8.0).toInt()
    val chunkMask = BitSet.valueOf(dataStream.readNBytes(chunkMaskSize))

    val chunkData = openCompressedStream(dataStream)
    val tileEntitiesData = loadRawData(dataStream)
    if (dataStream.readBoolean()) loadRawData(dataStream) else ByteArray(0)
    val extraTag = readCompressedCompound(dataStream)

    chunkData.close()
    dataStream.close()

    val loader = SlimeChunkDeserializerV9(tileEntitiesData, depth = depth, width = width, chunkMinX, chunkMinZ, chunkMask)

    return SlimeFile(
        worldVersion = worldVersion,
        chunkFlags = EnumSet.noneOf(V13AdditionalWorldData::class.java),
        extraTag = extraTag,
        chunks = loader.readChunks(chunkData)
    )
}

class SlimeChunkDeserializerV13(val flags: EnumSet<V13AdditionalWorldData>) {

    private val arraySize = 16 * 16 * 16 / (8 / 4)

    fun readChunks(chunkDataStream: DataInputStream): MutableMap<Long, SlimeChunk> {
        val size = chunkDataStream.readInt()
        val chunks = HashMap<Long, SlimeChunk>(size)

        for (i in 0 until size) {
            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val sections = readSections(chunkDataStream)

            val heightMapNBT = readLimitedCompound(chunkDataStream)

            val tileEntities = readTileEntities(chunkDataStream)
            val entityNBT = readEntities(chunkDataStream)
            val extraNBT = readLimitedCompound(chunkDataStream)

            val poiChunk: CompoundBinaryTag? = if(flags.contains(V13AdditionalWorldData.POI_CHUNKS)) {
                readLimitedCompound(chunkDataStream)
            } else null

            val blockTicks: ListBinaryTag? = if(flags.contains(V13AdditionalWorldData.BLOCK_TICKS)) {
                readLimitedCompound(chunkDataStream).getList("block_ticks", BinaryTagTypes.COMPOUND)
            } else null

            val fluidTicks: ListBinaryTag? = if(flags.contains(V13AdditionalWorldData.FLUID_TICKS)) {
                readLimitedCompound(chunkDataStream).getList("fluid_ticks", BinaryTagTypes.COMPOUND)
            } else null

            val chunk = SlimeChunk(chunkX, chunkZ, sections, heightMapNBT, poiChunk, blockTicks,
                fluidTicks, tileEntities, entityNBT, extraNBT)
            chunks[getChunkIndex(chunkX, chunkZ)] = chunk
        }

        return chunks
    }

    private fun readTileEntities(chunkDataStream: DataInputStream): ListBinaryTag {
        val compound = readLimitedCompound(chunkDataStream)
        return if (compound.isEmpty()) ListBinaryTag.empty()
        else compound.getList("tileEntities", BinaryTagTypes.COMPOUND)
    }

    private fun readEntities(chunkDataStream: DataInputStream): ListBinaryTag {
        val compound = readLimitedCompound(chunkDataStream)
        return if (compound.isEmpty()) ListBinaryTag.empty()
        else compound.getList("entities", BinaryTagTypes.COMPOUND)
    }

    private fun readSections(chunkDataStream: DataInputStream): Array<SlimeSection> {
        val sections: Array<SlimeSection> = Array(chunkDataStream.readInt()) { DUMMY_SECTION }

        for(sectionId in sections.indices) {
            val sectionFlags = chunkDataStream.readByte().toInt()

            val blockLightArray: ByteArray? = if ((sectionFlags and 1) == 1) {
                ByteArray(arraySize).also { chunkDataStream.readFully(it) }
            } else null
            val skyLightArray: ByteArray? = if (((sectionFlags shr 1) and 1) == 1) {
                ByteArray(arraySize).also { chunkDataStream.readFully(it) }
            } else null

            val blockStateTag = readLimitedCompound(chunkDataStream)
            val biomeTag = readLimitedCompound(chunkDataStream)

            sections[sectionId] = SlimeSection(sectionId, blockStateTag, biomeTag, blockLightArray, skyLightArray)
        }

        return sections
    }
}

class SlimeChunkDeserializerV12 {

    private val arraySize = 16 * 16 * 16 / (8 / 4)

    fun readChunks(chunkDataStream: DataInputStream): MutableMap<Long, SlimeChunk> {
        val size = chunkDataStream.readInt()
        val chunks = HashMap<Long, SlimeChunk>(size)

        for (i in 0 until size) {
            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val sections = readSections(chunkDataStream)

            val heightMapNBT = readLimitedCompound(chunkDataStream)

            val tileEntities = readTileEntities(chunkDataStream)
            val entityNBT = readEntities(chunkDataStream)
            val extraNBT = readLimitedCompound(chunkDataStream)

            val chunk = SlimeChunk(chunkX, chunkZ, sections, heightMapNBT, null,
                null, null, tileEntities, entityNBT, extraNBT)
            chunks[getChunkIndex(chunkX, chunkZ)] = chunk
        }

        return chunks
    }

    private fun readTileEntities(chunkDataStream: DataInputStream): ListBinaryTag {
        val compound = readLimitedCompound(chunkDataStream)
        return if (compound.isEmpty()) ListBinaryTag.empty()
        else compound.getList("tileEntities", BinaryTagTypes.COMPOUND)
    }

    private fun readEntities(chunkDataStream: DataInputStream): ListBinaryTag {
        val compound = readLimitedCompound(chunkDataStream)
        return if (compound.isEmpty()) ListBinaryTag.empty()
        else compound.getList("entities", BinaryTagTypes.COMPOUND)
    }

    private fun readSections(chunkDataStream: DataInputStream): Array<SlimeSection> {
        val sections: Array<SlimeSection> = Array(chunkDataStream.readInt()) { DUMMY_SECTION }

        for(sectionId in sections.indices) {
            val blockLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).also { chunkDataStream.readFully(it) }
            } else null
            val skyLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).also { chunkDataStream.readFully(it) }
            } else null

            val blockStateTag = readLimitedCompound(chunkDataStream)
            val biomeTag = readLimitedCompound(chunkDataStream)

            sections[sectionId] = SlimeSection(sectionId, blockStateTag, biomeTag, blockLightArray, skyLightArray)
        }

        return sections
    }
}

class SlimeChunkDeserializerV11 {

    private val arraySize = 16 * 16 * 16 / (8 / 4)

    fun readChunks(chunkDataStream: DataInputStream): MutableMap<Long, SlimeChunk> {
        val size = chunkDataStream.readInt()
        val chunks = HashMap<Long, SlimeChunk>(size)

        for (i in 0 until size) {
            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val sections = readSections(chunkDataStream)

            val heightMapNBT = readLimitedCompound(chunkDataStream)
            val tileEntitiesCompound = readCompressedCompound(chunkDataStream)
            val entityNBTCompound = readCompressedCompound(chunkDataStream)

            chunks[getChunkIndex(chunkX, chunkZ)] = SlimeChunk(chunkX, chunkZ, sections,
                heightMapNBT, null, null, null, tileEntitiesCompound.getList("tileEntities", BinaryTagTypes.COMPOUND),
                entityNBTCompound.getList("entities", BinaryTagTypes.COMPOUND), CompoundBinaryTag.empty())
        }

        return chunks
    }

    private fun readSections(chunkDataStream: DataInputStream): Array<SlimeSection> {
        val sections: Array<SlimeSection> = Array(chunkDataStream.readInt()) { DUMMY_SECTION }

        for(sectionId in sections.indices) {
            val blockLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).also { chunkDataStream.readFully(it) }
            } else null
            val skyLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).also { chunkDataStream.readFully(it) }
            } else null

            val blockStateTag = readLimitedCompound(chunkDataStream)
            val biomeTag = readLimitedCompound(chunkDataStream)

            sections[sectionId] = SlimeSection(sectionId, blockStateTag, biomeTag, blockLightArray, skyLightArray)
        }

        return sections
    }
}

private class SlimeChunkDeserializerV10 {

    val arraySize = 16 * 16 * 16 / (8 / 4)

    fun readChunks(chunkDataStream: DataInputStream): MutableMap<Long, SlimeChunk> {
        val size = chunkDataStream.readInt()
        val chunks = HashMap<Long, SlimeChunk>(size)

        for (i in 0 until size) {
            val chunkX = chunkDataStream.readInt()
            val chunkZ = chunkDataStream.readInt()

            val heightMapNBT = readLimitedCompound(chunkDataStream)
            val sections = readSections(chunkDataStream)

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
                ByteArray(arraySize).also { chunkDataStream.readFully(it) }
            } else null
            val skyLightArray: ByteArray? = if (chunkDataStream.readBoolean()) {
                ByteArray(arraySize).also { chunkDataStream.readFully(it) }
            } else null

            val blockStateTag = readLimitedCompound(chunkDataStream)
            val biomeTag = readLimitedCompound(chunkDataStream)

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

    fun readChunks(chunkDataStream: DataInputStream): MutableMap<Long, SlimeChunk> {
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

        chunkDataStream.close()
        return tempChunks
    }

    private fun readChunk(
        chunkDataStream: DataInputStream,
        chunkX: Int,
        chunkZ: Int,
    ): SlimeChunk {
        val heightMapNBT = readLimitedCompound(chunkDataStream)
        val readChunkSections = readChunkSections(chunkDataStream)

        return SlimeChunk(chunkX, chunkZ, readChunkSections, heightMapNBT, null, null, null,
            ListBinaryTag.empty(), ListBinaryTag.empty(), CompoundBinaryTag.empty())
    }

    private fun readChunkSections(dataStream: DataInputStream): Array<SlimeSection> {
        dataStream.readInt()
        dataStream.readInt()
        val sectionCount = dataStream.readInt()

        val sections = Array(sectionCount) { DUMMY_SECTION }

        for (chunkSection in 0 until sectionCount) {
            dataStream.readInt()

            var blockLightArray: ByteArray? = null
            if (dataStream.readBoolean()) {
                blockLightArray = ByteArray(2048).also { dataStream.readFully(it) }
            }

            val blockStateTag = readLimitedCompound(dataStream)

            val biomeTag = readLimitedCompound(dataStream)

            var skyLightArray: ByteArray? = null
            if (dataStream.readBoolean()) {
                skyLightArray = ByteArray(2048).also { dataStream.readFully(it) }
            }

            val section = SlimeSection(chunkSection, blockStateTag, biomeTag, blockLightArray, skyLightArray)
            sections[chunkSection] = section
        }

        return sections
    }
}

@Deprecated("Use new helpers instead")
private fun loadRawData(dataStream: DataInputStream): ByteArray {
    val compressedData = ByteArray(dataStream.readInt())
    val uncompressedData = ByteArray(dataStream.readInt())
    dataStream.read(compressedData)
    Zstd.decompress(uncompressedData, compressedData)
    return uncompressedData
}
