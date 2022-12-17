package gg.astromc.slimeloader.loader

import gg.astromc.slimeloader.helpers.ChunkHelpers.getChunkIndex
import gg.astromc.slimeloader.helpers.NBTHelpers.readNBTTag
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.Section
import net.minestom.server.instance.block.Block
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTList
import org.jglrxavpok.hephaistos.nbt.NBTString
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.*
import kotlin.math.floor


internal class SlimeDeserializer(
    private val chunkData: ByteArray,
    private val tileEntityData: ByteArray,
    private val depth: Int,
    private val width: Int,
    private val chunkMinX: Short,
    private val chunkMinZ: Short,
    private val chunkMask: BitSet,
) {

    fun readChunks(): Map<Long, Chunk> {
        val chunkDataByteStream = ByteArrayInputStream(chunkData)
        val chunkDataStream = DataInputStream(chunkDataByteStream)

        val tempChunks = mutableMapOf<Long, Chunk>()
        for (chunkZ in 0 until depth) {
            for (chunkX in 0 until width) {
                val bitsetIndex = chunkZ * width + chunkX

                val realChunkX = chunkX + chunkMinX
                val realChunkZ = chunkZ + chunkMinZ

                if (chunkMask[bitsetIndex]) {
                    val chunk = readChunk(chunkDataStream, realChunkX, realChunkZ)
                    val chunkIndex = getChunkIndex(realChunkX, realChunkZ)
                    tempChunks[chunkIndex] = chunk
                }
            }
        }

        loadTileEntities(tempChunks)
        return tempChunks
    }

    private fun readChunk(
        chunkDataStream: DataInputStream,
        chunkX: Int,
        chunkZ: Int,
    ): SlimeChunk {
        // Getting the heightmap
        val heightMapSize = chunkDataStream.readInt()
        val heightMap = ByteArray(heightMapSize)
        chunkDataStream.read(heightMap)
        val heightMapNBT = readNBTTag(heightMap) ?: NBTCompound()


        var readChunkSections = readChunkSections(chunkDataStream)

        return SlimeChunk(chunkX, chunkZ, readChunkSections.sections, heightMapNBT, readChunkSections.minSection, readChunkSections.maxSection)
    }

    private fun readChunkSections(dataStream: DataInputStream): SlimeSectionData {
        val minSectionY = dataStream.readInt()
        val maxSectionY = dataStream.readInt()
        val sectionCount = dataStream.readInt()

        val sections = Array(sectionCount) { DUMMY_SECTION }

        for (chunkSection in 0 until sectionCount) {
            val chunkY = dataStream.readInt()

            var blockLightArray: ByteArray? = null
            if (dataStream.readBoolean()) {
                blockLightArray = ByteArray(2048)
                dataStream.read(blockLightArray)
            }

            val blockStateData = ByteArray(dataStream.readInt())
            dataStream.read(blockStateData)
            val blockStateTag = readNBTTag<NBTCompound>(blockStateData)!!

            val biomeData = ByteArray(dataStream.readInt())
            dataStream.read(biomeData)
            val biomeTag = readNBTTag<NBTCompound>(biomeData)!!

            var skyLightArray: ByteArray? = null
            if (dataStream.readBoolean()) {
                skyLightArray = ByteArray(2048)
                dataStream.read(skyLightArray)
            }

            val section = SlimeSection(blockStateTag, biomeTag, blockLightArray, skyLightArray)
            sections[chunkY] = section
        }

        return SlimeSectionData(sections, minSectionY, maxSectionY)
    }

    private fun getBlockFromCompound(compound: NBTCompound): Block? {
        val name = compound.getString("Name") ?: return null
        if (name == "minecraft:air") return null
        val properties = compound.getCompound("Properties") ?: NBTCompound()

        val newProps = mutableMapOf<String, String>()
        for ((key, rawValue) in properties) {
            newProps[key] = (rawValue as NBTString).value
        }
        return Block.fromNamespaceId(name)?.withProperties(newProps)
    }

    private fun loadTileEntities(chunks: Map<Long, Chunk>) {
        val tileEntitiesCompound = readNBTTag<NBTCompound>(tileEntityData) ?: return
        val tileEntities = tileEntitiesCompound.getList<NBTCompound>("tiles") ?: return
        for (tileEntity in tileEntities) {
            val x = tileEntity.getInt("x") ?: continue
            val y = tileEntity.getInt("y") ?: continue
            val z = tileEntity.getInt("z") ?: continue

            val localX = x % 16 + (if(x < 0) 16 else 0)
            val localZ = z % 16 + (if(z < 0) 16 else 0)

            val chunkX = floor(x / 16.0).toInt()
            val chunkZ = floor(z / 16.0).toInt()

            val chunk = chunks[getChunkIndex(chunkX, chunkZ)] ?: continue
            var block = chunk.getBlock(localX, y, localZ)

            val id: String? = tileEntity.getString("id")
            if (id != null) {
                val blockHandler = MinecraftServer.getBlockManager().getHandler(id)
                if (blockHandler != null) {
                    block = block.withHandler(blockHandler)
                }
            }

            val compactedTileEntity = tileEntity.withRemovedKeys("x", "y", "z", "id", "keepPacked")
            if (compactedTileEntity.size > 0) {
                block = block.withNbt(compactedTileEntity)
            }

            chunk.setBlock(localX, y, localZ, block)
        }
    }

}
