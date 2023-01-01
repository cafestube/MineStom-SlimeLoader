package eu.cafestube.slimeloader.helpers


import org.jglrxavpok.hephaistos.mca.AnvilException
import org.jglrxavpok.hephaistos.mca.BiomePalette
import org.jglrxavpok.hephaistos.mca.ChunkSection
import org.jglrxavpok.hephaistos.mca.readers.SectionBiomeInformation
import org.jglrxavpok.hephaistos.mca.unpack
import org.jglrxavpok.hephaistos.mcdata.Biome
import org.jglrxavpok.hephaistos.nbt.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.log2

object NBTHelpers {

    inline fun <reified T : NBT> readNBTTag(bytes: ByteArray): T?
            = NBTReader(ByteArrayInputStream(bytes), CompressedProcesser.NONE).read() as? T

    inline fun <reified T : NBT> writeNBTTag(tag: T): ByteArray
            = ByteArrayOutputStream().also { NBTWriter(it).writeRaw(tag) }.toByteArray()

    //Method stolen from Minestoms nbt library thingy
    fun readBiomes(biomesNBT: NBTCompound): SectionBiomeInformation {
        var biomes: Array<String>?
        val paletteNBT = biomesNBT.getList<NBTString>("palette") ?: AnvilException.missing("biomes.palette")
        val biomePalette = BiomePalette(paletteNBT)
        if("data" !in biomesNBT) {
            if(biomePalette.elements.size > 0) {
                return SectionBiomeInformation(biomes = null, baseBiome = biomePalette.elements[0])
            }
            return SectionBiomeInformation()
        } else {
            biomes = Array(ChunkSection.BiomeArraySize) { Biome.UnknownBiome }
            val ids = getUncompressedBiomeIndices(biomesNBT)
            for ((index, id) in ids.withIndex()) {
                biomes[index] = biomePalette.elements[id]
            }
            return SectionBiomeInformation(biomes = biomes, baseBiome = null)
        }
    }

    //Also stolen from Minestoms nbt library
    fun getUncompressedBiomeIndices(biomesNBT: NBTCompound): IntArray {
        val biomePalette = biomesNBT.getList<NBTString>("palette")!!
        return if(biomePalette.size == 1) {
            IntArray(ChunkSection.BiomeArraySize) { 0 }
        } else {
            val compressedBiomes = biomesNBT.getLongArray("data")!!

            val sizeInBits = ceil(log2(biomePalette.size.toDouble())).toInt()
            val intPerLong = 64 / sizeInBits
            val expectedCompressedLength = ceil(ChunkSection.BiomeArraySize.toDouble() / intPerLong).toInt()
            if (compressedBiomes.size != expectedCompressedLength) {
                throw AnvilException("Invalid compressed biomes length (${compressedBiomes.size}). At $sizeInBits bit per value, expected $expectedCompressedLength bytes")
            }
            unpack(compressedBiomes, sizeInBits).sliceArray(0 until ChunkSection.BiomeArraySize)
        }
    }

    //I'm once again here to tell you, that I have stolen from minestoms nbt library
    fun loadBlockData(blockStateTag: NBTCompound): IntArray {
        val compactedBlockStates = blockStateTag.getLongArray("data") ?: return IntArray(0)
        val sizeInBits = compactedBlockStates.size*64 / 4096

        val expectedCompressedLength =
            if(compactedBlockStates.size == 0) {
                -1 /* force invalid value */
            } else {
                val intPerLong = 64 / sizeInBits
                ceil(4096.0 / intPerLong).toInt()
            }
        var unpack = true
        if(compactedBlockStates.size != expectedCompressedLength) {
            if(compactedBlockStates.size == 0) {
                // palette only has a single element
                unpack = false
            } else {
                throw AnvilException("Invalid compressed BlockStates length (${compactedBlockStates.size}). At $sizeInBits bit per value, expected $expectedCompressedLength bytes. Note that 0 length is not allowed with pre 1.18 formats.")
            }
        }

        return if(unpack) {
            unpack(compactedBlockStates, sizeInBits).sliceArray(0 until 4096)
        } else {
            IntArray(4096) { 0 }
        }
    }

}