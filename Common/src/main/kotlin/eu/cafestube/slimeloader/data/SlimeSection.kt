package eu.cafestube.slimeloader.data

import net.kyori.adventure.nbt.CompoundBinaryTag


val DUMMY_SECTION = SlimeSection(0, CompoundBinaryTag.empty(), CompoundBinaryTag.empty(), null, null)

data class SlimeSection(
    val index: Int,
    val blockStateTag: CompoundBinaryTag,
    val biomeTag: CompoundBinaryTag,
    val blockLight: ByteArray?,
    val skyLight: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SlimeSection) return false

        if (index != other.index) return false
        if (blockStateTag != other.blockStateTag) return false
        if (biomeTag != other.biomeTag) return false
        if (!blockLight.contentEquals(other.blockLight)) return false
        if (!skyLight.contentEquals(other.skyLight)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + blockStateTag.hashCode()
        result = 31 * result + biomeTag.hashCode()
        result = 31 * result + (blockLight?.contentHashCode() ?: 0)
        result = 31 * result + (skyLight?.contentHashCode() ?: 0)
        return result
    }
}
