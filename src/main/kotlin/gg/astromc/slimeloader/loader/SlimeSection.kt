package gg.astromc.slimeloader.loader

import org.jglrxavpok.hephaistos.nbt.NBTCompound

val DUMMY_SECTION = SlimeSection(NBTCompound(), NBTCompound(), null, null)

data class SlimeSection(val blockStateTag: NBTCompound, val biomeTag: NBTCompound, val blockLight: ByteArray?, val skyLight: ByteArray?)

data class SlimeSectionData(val sections: Array<SlimeSection>, val minSection: Int, val maxSection: Int)