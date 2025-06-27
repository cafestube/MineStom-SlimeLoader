package eu.cafestube.slimeloader.helpers


import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.BinaryTagIO
import java.io.ByteArrayInputStream


inline fun <reified T : BinaryTag> readNBTTag(bytes: ByteArray): T?
        = BinaryTagIO.reader(Long.MAX_VALUE).read(ByteArrayInputStream(bytes), BinaryTagIO.Compression.NONE) as? T
