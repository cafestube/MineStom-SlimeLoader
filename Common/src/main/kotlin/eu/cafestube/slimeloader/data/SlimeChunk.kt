package eu.cafestube.slimeloader.data

import net.kyori.adventure.nbt.CompoundBinaryTag

class SlimeChunk(val x: Int, val z: Int, val sections: Array<SlimeSection>, val heightMaps: CompoundBinaryTag, val tileEntities: CompoundBinaryTag, val entities: CompoundBinaryTag, val extra: CompoundBinaryTag)