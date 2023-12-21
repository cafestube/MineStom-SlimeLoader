package gg.astromc.slimeloader.data

import eu.cafestube.slimeloader.data.SlimeFile

interface SlimeDataFixer {

    fun fixWorld(slimeFile: SlimeFile): SlimeFile

}