package gg.astromc.slimeloader.data

import eu.cafestube.slimeloader.data.SlimeFile

object NoOpSlimeFixer : SlimeDataFixer {
    override fun fixWorld(slimeFile: SlimeFile): SlimeFile {
        return slimeFile
    }
}