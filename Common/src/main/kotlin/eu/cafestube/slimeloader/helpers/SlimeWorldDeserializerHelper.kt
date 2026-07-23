package eu.cafestube.slimeloader.helpers

import com.github.luben.zstd.ZstdInputStream
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.io.BufferedInputStream
import java.io.DataInput
import java.io.DataInputStream

fun openCompressedStream(stream: DataInputStream): DataInputStream {
    val compressedLength = stream.readInt()
    stream.readInt()

    val limitedInputStream = LimitedInputStream(stream, compressedLength)
    val inputStream = ZstdInputStream(limitedInputStream)
    return DataInputStream(BufferedInputStream(inputStream))
}

fun readLimitedCompound(stream: DataInputStream): CompoundBinaryTag {
    val length = stream.readInt()
    if (length == 0) return CompoundBinaryTag.empty()

    val limitedInputStream = LimitedInputStream(stream, length)
    val tag = BinaryTagIO.unlimitedReader().read(DataInputStream(limitedInputStream) as DataInput)

    limitedInputStream.drainRemaining()
    return tag
}

fun readCompressedCompound(stream: DataInputStream): CompoundBinaryTag {
    val compressedLength = stream.readInt()
    val decompressedLength = stream.readInt()

    if (decompressedLength == 0) return CompoundBinaryTag.empty()

    val limitedInputStream = LimitedInputStream(stream, compressedLength)
    ZstdInputStream(limitedInputStream).use { zstd ->
        //See https://github.com/InfernalSuite/AdvancedSlimePaper/pull/194 why we cast to DataInput and use DataInputStream here
        val tag = BinaryTagIO.unlimitedReader().read(DataInputStream(zstd) as DataInput)

        val buffer = ByteArray(512)
        while (zstd.read(buffer) != -1) {
            continue
        }

        return tag
    }
}
