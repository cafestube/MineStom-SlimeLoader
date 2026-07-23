package eu.cafestube.slimeloader.helpers


import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.BinaryTagIO
import java.io.ByteArrayInputStream
import java.io.DataInput
import java.io.DataInputStream


//See https://github.com/InfernalSuite/AdvancedSlimePaper/pull/194 why we cast to DataInput and use DataInputStream here
inline fun <reified T : BinaryTag> readNBTTag(bytes: ByteArray): T?
        = BinaryTagIO.reader(Long.MAX_VALUE).read(DataInputStream(ByteArrayInputStream(bytes)) as DataInput) as? T
