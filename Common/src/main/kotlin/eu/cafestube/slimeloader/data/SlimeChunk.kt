package eu.cafestube.slimeloader.data

import org.jglrxavpok.hephaistos.nbt.NBTCompound

class SlimeChunk(val x: Int, val z: Int, val sections: Array<SlimeSection>, val heightMaps: NBTCompound, val minSection: Int, val maxSection: Int)