package eu.cafestube.slimeloader.loader

import com.github.luben.zstd.ZstdOutputStream
import eu.cafestube.slimeloader.data.SlimeFile
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream
import kotlin.experimental.or

fun serialize(dataOutputStream: DataOutputStream, slimeFile: SlimeFile) {
    dataOutputStream.writeShort(0xB10B)
    dataOutputStream.writeByte(13) //SlimeFile version 13

    dataOutputStream.writeInt(slimeFile.worldVersion)
    dataOutputStream.writeByte(V13AdditionalWorldData.fromSet(slimeFile.chunkFlags).toInt())

    writeCompressed(dataOutputStream) { writeChunks(it, slimeFile) }

    writeCompressed(dataOutputStream) { stream ->
        BinaryTagIO.writer().write(
            slimeFile.extraTag ?: CompoundBinaryTag.empty(),
            stream as DataOutput
        )
    }

    dataOutputStream.close()
}

private fun writeCompressed(dataOutputStream: DataOutputStream, writer: (DataOutputStream) -> Unit) {
    val compressedOut = ByteArrayOutputStream()
    val zstd = ZstdOutputStream(compressedOut)
    val dataOut = DataOutputStream(zstd)

    writer(dataOut)

    dataOut.flush()
    zstd.close()

    val compressed = compressedOut.toByteArray()

    dataOutputStream.writeInt(compressed.size)
    dataOutputStream.writeInt(dataOut.size())
    dataOutputStream.write(compressed)
}

private fun writeChunks(dataOutputStream: DataOutputStream, slimeFile: SlimeFile) {
    dataOutputStream.writeInt(slimeFile.chunks.size)
    slimeFile.chunks.forEach { _, chunk ->
        dataOutputStream.writeInt(chunk.x)
        dataOutputStream.writeInt(chunk.z)

        dataOutputStream.writeInt(chunk.sections.size)
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
            dataOutputStream.writeByte(sectionFlags.toInt())

            if(hasBlockLight) {
                dataOutputStream.write(section.blockLight)
            }

            if(hasSkyLight) {
                dataOutputStream.write(section.skyLight)
            }

            serializeCompoundTag(section.blockStateTag, dataOutputStream)
            serializeCompoundTag(section.biomeTag, dataOutputStream)
        }

        serializeCompoundTag(chunk.heightMaps, dataOutputStream)

        serializeCompoundTag(
            CompoundBinaryTag.builder()
                .put("tile_entities", chunk.tileEntities)
                .build(),
            dataOutputStream
        )

        serializeCompoundTag(
            CompoundBinaryTag.builder()
                .put("entities", chunk.entities)
                .build(),
            dataOutputStream
        )

        serializeCompoundTag(chunk.extra, dataOutputStream)
    }
}

private fun serializeCompoundTag(tag: CompoundBinaryTag, dataOutputStream: DataOutputStream) {
    if (tag.size() == 0) {
        dataOutputStream.writeInt(0)
        return
    }

    val outByteStream = ByteArrayOutputStream()
    BinaryTagIO.writer().write(tag, DataOutputStream(outByteStream) as DataOutput)

    val bytes = outByteStream.toByteArray()
    dataOutputStream.writeInt(bytes.size)
    dataOutputStream.write(bytes)
}
