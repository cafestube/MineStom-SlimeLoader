package gg.astromc.slimeloader

import gg.astromc.slimeloader.source.FileSlimeSource
import gg.astromc.slimeloader.source.SlimeSource
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.world.DimensionType
import gg.astromc.slimeloader.loader.SlimeLoader
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import java.io.File
import kotlin.system.measureTimeMillis


fun main() {
    val server = MinecraftServer.init()

//    val dimensionTypeManager = MinecraftServer.getDimensionTypeManager()
//    dimensionTypeManager.addDimension(slimeDimension)

    val instanceManager = MinecraftServer.getInstanceManager()

    val slimeLoader: IChunkLoader

    val file = File(System.getenv("TESTING_SLIME_FILE"))
    val slimeSource: SlimeSource = FileSlimeSource(file)
    val timeToLoad = measureTimeMillis { slimeLoader = SlimeLoader(slimeSource) }

    println("Took ${timeToLoad}ms to load map.")

    val instanceContainer = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, slimeLoader)
    val globalEventHandler = MinecraftServer.getGlobalEventHandler()
    globalEventHandler.addListener(AsyncPlayerConfigurationEvent::class.java) {
        val player = it.player
        player.respawnPoint = Pos(0.0, 120.0, 0.0)

        it.spawningInstance = instanceContainer
    }

    server.start("0.0.0.0", 25565)
}
