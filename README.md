# 🗺️ SlimeLoader

Slime loader is a map loader & saver for the file format Slime as specified [here](https://github.com/cafestube/Minestom-SlimeLoader/blob/master/SLIME_FORMAT.txt) implemented in Minestom.

Features:
```
- [x] World loading
  - [x] Blocks
  - [ ] TileEntities
  - [ ] Entities
  - [x] Extra Data
        (Data will be loaded into the instance's "Data" Tag)
- [ ] World saving
  - [ ] Blocks
  - [ ] TileEntities
  - [ ] Entities
  - [ ] Extra Data
        (Data from "Data" Tag will be saved)
- [ ] Async
```

## Installation

Add the following to your `build.gradle.kts`

```kotlin
repositories { 
  maven("https://repo.cafestu.be/repository/maven-public-snapshots/")
}

dependencies { 
  implementation("eu.cafestube:Minestom-SlimeLoader:1.0.1-SNAPSHOT")
}
```

## Usage

The library is quite simple to use. If you need to get your slime world from somewhere else (ex. AWS S3) you can implement the `SlimeSource` interface. 

#### Kotlin

```kotlin
val instanceManager = MinecraftServer.getInstanceManager()

val file = File("Slime file goes here")
val slimeSource: SlimeSource = FileSlimeSource(file)
val slimeLoader: IChunkLoader = SlimeLoader(instanceContainer, slimeSource)
val instanceContainer = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, slimeLoader)

```

#### Java

```java
InstanceManager instanceManager = MinecraftServer.getInstanceManager();

File file = new File("Slime file goes here");
SlimeSource slimeSource = new FileSlimeSource(file);
SlimeLoader slimeLoader = new SlimeLoader(slimeSource, false);
InstanceContainer instanceContainer = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, slimeLoader);
```

## License

SlimeLoader is licensed under the MIT license

###### Written by [Cody](https://github.com/CatDevz) for AstroMC updated by the CafeStube Dev Team for cafestu.be
