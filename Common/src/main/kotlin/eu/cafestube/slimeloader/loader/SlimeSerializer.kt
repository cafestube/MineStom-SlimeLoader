package eu.cafestube.slimeloader.loader

import com.github.luben.zstd.Zstd
import eu.cafestube.slimeloader.data.SlimeFile
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.experimental.or

fun serialize(dataOutputStream: DataOutputStream, slimeFile: SlimeFile) {
    dataOutputStream.writeShort(0xB10B)
    dataOutputStream.writeByte(13) //SlimeFile version 13

    dataOutputStream.writeInt(slimeFile.worldVersion)
    dataOutputStream.writeByte(V13AdditionalWorldData.fromSet(slimeFile.chunkFlags).toInt())

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
            val hasSkyLight = section.skyLight != null

            var sectionFlags: Byte = 0
            if (hasBlockLight) {
                sectionFlags = (sectionFlags or 1.toByte())
            }
            if (hasSkyLight) {
                sectionFlags = (sectionFlags or (1 shl 1).toByte())
            }
            dataStream.writeByte(sectionFlags.toInt())

            if(hasBlockLight) {
                dataStream.write(section.blockLight)
            }

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

        val tileEntities = serializeCompoundTag(
            CompoundBinaryTag.builder()
                .put("tile_entities", chunk.tileEntities)
                .build()
        )
        dataStream.writeInt(tileEntities.size)
        dataStream.write(tileEntities)

        val entities = serializeCompoundTag(
            CompoundBinaryTag.builder()
                .put("entities", chunk.entities)
                .build()
        )
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
