package gg.astromc.slimeloader.helpers

object ChunkHelpers {

    fun getChunkIndex(x: Int, z: Int): Long = (x.toLong() shl 32) + z

}