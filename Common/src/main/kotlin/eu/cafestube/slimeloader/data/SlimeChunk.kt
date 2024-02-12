package eu.cafestube.slimeloader.data

import org.jglrxavpok.hephaistos.nbt.NBTCompound

class SlimeChunk(val x: Int, val z: Int, val sections: Array<SlimeSection>, val heightMaps: NBTCompound, val tileEntities: NBTCompound, val entities: NBTCompound, val extra: NBTCompound)