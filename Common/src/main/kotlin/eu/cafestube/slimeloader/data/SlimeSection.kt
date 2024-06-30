package eu.cafestube.slimeloader.data

import net.kyori.adventure.nbt.CompoundBinaryTag


val DUMMY_SECTION = SlimeSection(0, CompoundBinaryTag.empty(), CompoundBinaryTag.empty(), null, null)

data class SlimeSection(val index: Int, val blockStateTag: CompoundBinaryTag, val biomeTag: CompoundBinaryTag, val blockLight: ByteArray?, val skyLight: ByteArray?)
