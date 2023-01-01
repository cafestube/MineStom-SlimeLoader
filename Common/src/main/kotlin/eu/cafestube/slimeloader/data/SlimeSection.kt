package eu.cafestube.slimeloader.data

import org.jglrxavpok.hephaistos.nbt.NBTCompound

val DUMMY_SECTION = SlimeSection(0, NBTCompound(), NBTCompound(), null, null)

data class SlimeSection(val index: Int, val blockStateTag: NBTCompound, val biomeTag: NBTCompound, val blockLight: ByteArray?, val skyLight: ByteArray?)

data class SlimeSectionData(val sections: Array<SlimeSection>, val minSection: Int, val maxSection: Int)