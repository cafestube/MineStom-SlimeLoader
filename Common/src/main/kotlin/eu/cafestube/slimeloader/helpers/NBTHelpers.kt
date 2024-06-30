package eu.cafestube.slimeloader.helpers


import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import java.io.ByteArrayInputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2

object NBTHelpers {

    private const val BlockStateSize = 16*16*16
    private const val BiomeArraySize = 4*4*4

    inline fun <reified T : BinaryTag> readNBTTag(bytes: ByteArray): T?
            = BinaryTagIO.reader(Long.MAX_VALUE).read(ByteArrayInputStream(bytes), BinaryTagIO.Compression.NONE) as? T

    
    //Also stolen from Minestoms nbt library
    fun getUncompressedBiomeIndices(biomesNBT: CompoundBinaryTag): IntArray {
        val biomePalette = biomesNBT.getList("palette")
        return if(biomePalette.size() == 1) {
            IntArray(BiomeArraySize) { 0 }
        } else {
            val compressedBiomes = biomesNBT.getLongArray("data")

            val sizeInBits = ceil(log2(biomePalette.size().toDouble())).toInt()
            val intPerLong = 64 / sizeInBits
            val expectedCompressedLength = ceil(BiomeArraySize.toDouble() / intPerLong).toInt()
            if (compressedBiomes.size != expectedCompressedLength) {
                throw IllegalStateException("Invalid compressed biomes length (${compressedBiomes.size}). At $sizeInBits bit per value, expected $expectedCompressedLength bytes")
            }
            unpack(compressedBiomes, sizeInBits).sliceArray(0 until BiomeArraySize)
        }
    }

    //I'm once again here to tell you, that I have stolen from minestoms nbt library
    fun loadBlockData(blockStateTag: CompoundBinaryTag): IntArray {
        val compactedBlockStates = blockStateTag.getLongArray("data") ?: return IntArray(0)
        val sizeInBits = compactedBlockStates.size*64 / 4096

        val expectedCompressedLength =
            if(compactedBlockStates.isEmpty()) {
                -1 /* force invalid value */
            } else {
                val intPerLong = 64 / sizeInBits
                ceil(4096.0 / intPerLong).toInt()
            }
        var unpack = true
        if(compactedBlockStates.size != expectedCompressedLength) {
            if(compactedBlockStates.isEmpty()) {
                // palette only has a single element
                unpack = false
            } else {
                throw IllegalStateException("Invalid compressed BlockStates length (${compactedBlockStates.size}). At $sizeInBits bit per value, expected $expectedCompressedLength bytes. Note that 0 length is not allowed with pre 1.18 formats.")
            }
        }

        return if(unpack) {
            unpack(compactedBlockStates, sizeInBits).sliceArray(0 until 4096)
        } else {
            IntArray(4096) { 0 }
        }
    }


    /**
     * Unpacks int values of 'lengthInBits' bits from a long array.
     * Contrary to decompress, this method will produce unused bits and do not overflow remaining bits to the next long.
     *
     * (ie 2 >32 bit long values will produce two longs, but the highest bits of each long will be unused)
     */
    private fun unpack(longs: LongArray, lengthInBits: Int): IntArray {
        val intPerLong = floor(64.0 / lengthInBits)
        val intCount = ceil(longs.size * intPerLong).toInt()
        val ints = IntArray(intCount)
        val intPerLongCeil = ceil(intPerLong).toInt()
        val mask = (1 shl lengthInBits)-1L
        for(i in ints.indices) {
            val longIndex = i / intPerLongCeil
            val subIndex = i % intPerLongCeil
            val value = ((longs[longIndex] shr (subIndex*lengthInBits)) and mask).toInt()
            ints[i] = value
        }
        return ints
    }



}