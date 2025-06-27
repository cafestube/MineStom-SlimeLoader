package eu.cafestube.slimeloader.pruner

import eu.cafestube.slimeloader.data.SlimeChunk
import net.kyori.adventure.nbt.BinaryTagTypes
import net.kyori.adventure.nbt.CompoundBinaryTag

fun SlimeChunk.canBePruned(): Boolean {
    if(tileEntities.size() == 0)
        return false
    if(entities.size() == 0)
        return false

    sections.forEach {
        val palette = it.blockStateTag.getList("palette")
        if(palette.elementType() != BinaryTagTypes.COMPOUND) {
            return@forEach // Invalid palette, so probably empty. Continue checking
        }
        if(palette.size() > 1) return false // More than one block state, so not empty
        if(palette.size() == 0) return@forEach //This section is empty, so we can skip checking this one

        val blockState = palette.get(0) as CompoundBinaryTag
        if(blockState.getString("Name") != "minecraft:air") {
            return false // Not empty, so we cannot prune
        }
    }

    return true
}