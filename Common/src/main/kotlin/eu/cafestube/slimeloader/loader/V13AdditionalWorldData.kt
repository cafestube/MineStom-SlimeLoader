package eu.cafestube.slimeloader.loader

import java.util.EnumSet

enum class V13AdditionalWorldData {
    POI_CHUNKS,
    BLOCK_TICKS,
    FLUID_TICKS;

    fun isSet(bitset: Byte): Boolean {
        return ((bitset.toInt() shr ordinal) and 1) == 1
    }

    companion object {
        fun countUnsupportedFlags(bitset: Byte): Int {
            var supportedFlagsMask = 0
            for (data in V13AdditionalWorldData.entries) {
                supportedFlagsMask = supportedFlagsMask or (1 shl data.ordinal)
            }
            val unsupportedFlagsMask = bitset.toInt() and supportedFlagsMask.inv()
            return Integer.bitCount(unsupportedFlagsMask)
        }

        fun fromSet(set: EnumSet<V13AdditionalWorldData>): Byte {
            var bitset: Byte = 0
            for (data in set) {
                bitset = (bitset.toInt() or (1 shl data.ordinal)).toByte()
            }
            return bitset
        }
    }
}
