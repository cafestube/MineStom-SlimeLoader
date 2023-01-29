package eu.cafestube.slimeloader.data

import org.jglrxavpok.hephaistos.nbt.NBTCompound
import java.util.*

class SlimeFile(
    val chunkMinX: Short,
    val chunkMinZ: Short,
    val width: Int,
    val depth: Int,

    val extraTag: NBTCompound?,
    val chunks: Map<Long, SlimeChunk>
)