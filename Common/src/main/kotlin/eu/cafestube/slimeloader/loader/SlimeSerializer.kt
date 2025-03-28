package eu.cafestube.slimeloader.loader

import com.github.luben.zstd.Zstd
import eu.cafestube.slimeloader.data.SlimeFile
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

fun serialize(dataOutputStream: DataOutputStream, slimeFile: SlimeFile) {
    dataOutputStream.writeShort(0xB10B)
    dataOutputStream.writeByte(12) //SlimeFile version 12

    dataOutputStream.writeInt(slimeFile.worldVersion)
    val serializedChunks = serializeChunks(slimeFile)
    val compressedChunkData = Zstd.compress(serializedChunks)
    dataOutputStream.writeInt(compressedChunkData.size)
    dataOutputStream.writeInt(serializedChunks.size)
    dataOutputStream.write(compressedChunkData)

    val extra = serializeCompoundTag(slimeFile.extraTag ?: CompoundBinaryTag.empty())
    val compressedExtra = Zstd.compress(extra)
    dataOutputStream.writeInt(compressedExtra.size)
    dataOutputStream.writeInt(extra.size)
    dataOutputStream.write(compressedExtra)

    dataOutputStream.close()
}

private fun serializeChunks(slimeFile: SlimeFile): ByteArray {
    val byteStream = ByteArrayOutputStream(16384)
    val dataStream = DataOutputStream(byteStream)

    dataStream.writeInt(slimeFile.chunks.size)
    slimeFile.chunks.forEach { _, chunk ->
        dataStream.writeInt(chunk.x)
        dataStream.writeInt(chunk.z)

        dataStream.writeInt(chunk.sections.size)
        chunk.sections.forEach { section ->
            val hasBlockLight = section.blockLight != null
            dataStream.writeBoolean(hasBlockLight)
            if(hasBlockLight) {
                dataStream.write(section.blockLight)
            }

            val hasSkyLight = section.skyLight != null
            dataStream.writeBoolean(hasSkyLight)
            if(hasSkyLight) {
                dataStream.write(section.skyLight)
            }

            val serializedBlockStates = serializeCompoundTag(section.blockStateTag)
            dataStream.writeInt(serializedBlockStates.size)
            dataStream.write(serializedBlockStates)

            val serializedBiomes = serializeCompoundTag(section.biomeTag)
            dataStream.writeInt(serializedBiomes.size)
            dataStream.write(serializedBiomes)
        }

        val serializedHeightMap = serializeCompoundTag(chunk.heightMaps)
        dataStream.writeInt(serializedHeightMap.size)
        dataStream.write(serializedHeightMap)

        val tileEntities = serializeCompoundTag(chunk.tileEntities)
        dataStream.writeInt(tileEntities.size)
        dataStream.write(tileEntities)

        val entities = serializeCompoundTag(chunk.entities)
        dataStream.writeInt(entities.size)
        dataStream.write(entities)

        val extra = serializeCompoundTag(chunk.extra)
        dataStream.writeInt(extra.size)
        dataStream.write(extra)
    }

    return byteStream.toByteArray()
}

private fun serializeCompoundTag(tag: CompoundBinaryTag): ByteArray {
    if (tag.size() == 0) return ByteArray(0)

    val outByteStream = ByteArrayOutputStream()
    BinaryTagIO.writer().write(tag, outByteStream)
    return outByteStream.toByteArray()
}
