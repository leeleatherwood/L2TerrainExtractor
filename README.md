# L2TerrainExtractor 🗺️# L2TerrainExtractor 🗺️# L2TerrainExtractor 🗺️



> **Lineage 2 Terrain Heightmap Extractor**Lineage 2 Terrain Heightmap Extractor



Extract G16 heightmap textures from encrypted Lineage 2 `.utx` terrain packages. Features automatic tile detection, XOR decryption for Ver 111/121+ packages, and outputs both PNG and RAW formats for each tile.> **Lineage 2 Terrain Heightmap Extractor**



[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)Extract G16 heightmap textures from encrypted Lineage 2 `.utx` terrain packages. Features automatic tile detection, XOR decryption for Ver 111/121+ packages, and outputs both PNG and RAW formats for each tile.

[![Java 17+](https://img.shields.io/badge/Java-17+-ED8B00.svg)](https://openjdk.org/)

[![Gradle 8.5](https://img.shields.io/badge/Gradle-8.5-02303A.svg)](https://gradle.org/)Extract G16 heightmap textures from encrypted Lineage 2 `.utx` terrain packages. Features automatic tile detection, XOR decryption for Ver 111/121+ packages, and outputs both PNG and RAW formats for each tile.



![L2 Heightmaps](l2_heightmaps.png)![L2 Heightmaps](l2_heightmaps.png)



## ✨ Features[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)



- 🔓 **Automatic Decryption** - Handles Lineage 2 Ver 111 and Ver 121+ XOR encryption[![Java 17+](https://img.shields.io/badge/Java-17+-ED8B00.svg)](https://openjdk.org/)![License](https://img.shields.io/badge/license-MIT-blue.svg) ![Java](https://img.shields.io/badge/Java-17+-ED8B00) ![Gradle](https://img.shields.io/badge/Gradle-8.5-02303A)

- 🧩 **Smart Tile Detection** - Automatically finds G16 data using marker pattern detection

- 📊 **Dual Output Formats** - PNG (8-bit normalized) for viewing, RAW (16-bit) for terrain editors[![Gradle 8.5](https://img.shields.io/badge/Gradle-8.5-02303A.svg)](https://gradle.org/)

- ⚡ **Fast Processing** - Processes 150+ tiles in seconds

## ✨ Features

## 📋 Requirements

![L2 Heightmaps](l2_heightmaps.png)

- **Java 17+**

- 🔓 **Automatic Decryption** - Handles Lineage 2 Ver 111 and Ver 121+ XOR encryption

## 🚀 Quick Start

## ✨ Features- 🧩 **Smart Tile Detection** - Automatically finds G16 data using marker pattern detection

### Installation

- 📊 **Dual Output Formats** - PNG (8-bit normalized) for viewing, RAW (16-bit) for terrain editors

1. Clone the repository:

```bash- 🔓 **Automatic Decryption** - Handles Lineage 2 Ver 111 and Ver 121+ XOR encryption- ⚡ **Fast Processing** - Processes 150+ tiles in seconds

git clone https://github.com/yourusername/L2TerrainExtractor.git

cd L2TerrainExtractor- 🧩 **Smart Tile Detection** - Automatically finds G16 data using marker pattern detection

```

- 📊 **Dual Output Formats** - PNG (8-bit normalized) for viewing, RAW (16-bit) for terrain editors## 📋 Requirements

2. Build the project:

```bash- ⚡ **Fast Processing** - Processes 150+ tiles in seconds

./gradlew build

```- Java 17 or later



This creates a fat JAR at `build/libs/l2terrain-1.0.0-all.jar` with all dependencies included.## 📋 Requirements



### Basic Usage## 🚀 Quick Start



Extract heightmaps from a directory of `.utx` terrain files:- **Java 17+**



```bash### Building

java -jar l2terrain-1.0.0-all.jar <input_directory> -o <output_directory>

```## 🚀 Quick Start



Each tile produces two files: `XX_YY_heightmap.png` and `XX_YY_heightmap.raw````bash



### Examples### Installation./gradlew build



Extract all terrain tiles to a heightmaps folder:```

```bash

java -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps1. Clone the repository:

```

```bashThis creates a fat JAR at `build/libs/l2terrain-1.0.0-all.jar` with all dependencies included.

Verbose mode to see each tile extracted:

```bashgit clone https://github.com/yourusername/L2TerrainExtractor.git

java -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps -v

```cd L2TerrainExtractor### Basic Usage



### CLI Options```



```Extract heightmaps from a directory of `.utx` terrain files:

Usage: l2terrain [-hVv] [-o=<output>] [-p=<pattern>] <inputDir>

2. Build the project:

Extract terrain heightmaps from Lineage 2 packages

```bash```bash

Parameters:

      <inputDir>          Input directory containing .utx terrain packages./gradlew buildjava -jar l2terrain-1.0.0-all.jar <input_directory> -o <output_directory>



Options:``````

  -o, --output=<output>   Output directory (default: current directory)

  -p, --pattern=<pattern> File pattern to match (default: t_*_*.utx)

  -v, --verbose           Verbose output

  -h, --help              Show this help message and exit.This creates a fat JAR at `build/libs/l2terrain-1.0.0-all.jar` with all dependencies included.Each tile produces two files: `XX_YY_heightmap.png` and `XX_YY_heightmap.raw`

  -V, --version           Print version information and exit.

```



## 🔧 How It Works### Basic Usage### Options



### Architecture



- **Extractors (`Extractor`)** - Handle different data formatsExtract heightmaps from a directory of `.utx` terrain files:```

  - *Current:* `HeightmapExtractor` (G16 textures)

  - *Potential:* Splatmap extractor, texture layer extractorUsage: l2terrain [-hVv] [-o=<output>] [-p=<pattern>] <inputDir>

  

- **Crypto (`L2Decryptor`)** - Handle package encryption```bash

  - *Current:* Ver 111 (fixed key) and Ver 121+ (filename-derived key) XOR decryption

  - *Potential:* Other L2 encryption versionsjava -jar l2terrain-1.0.0-all.jar <input_directory> -o <output_directory>Extract terrain heightmaps from Lineage 2 packages



### Technical Details```



#### EncryptionParameters:



Lineage 2 encrypted packages have a 28-byte UTF-16LE header (`Lineage2VerXXX`) followed by XOR-encrypted data:Each tile produces two files: `XX_YY_heightmap.png` and `XX_YY_heightmap.raw`      <inputDir>          Input directory containing .utx terrain packages



| Version | Key Derivation |

|---------|----------------|

| Ver 111 | Fixed XOR key `0xAC` |### ExamplesOptions:

| Ver 121+ | `sum(lowercase filename characters) & 0xFF` |

  -o, --output=<output>   Output directory (default: current directory)

#### G16 Format

Extract all terrain tiles to a heightmaps folder:  -p, --pattern=<pattern> File pattern to match (default: t_*_*.utx)

Heightmaps are stored as G16 textures (raw 16-bit grayscale):

- 256×256 pixels per tile```bash  -v, --verbose           Verbose output

- Little-endian unsigned 16-bit values

- Data follows marker pattern `00 40 80 10`java -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps  -h, --help              Show this help message and exit.



#### Tile Naming```  -V, --version           Print version information and exit.



Terrain tiles use naming convention `t_XX_YY.utx` where XX and YY are grid coordinates.```



## 📁 Project StructureVerbose mode to see each tile extracted:



``````bash### Examples

io.github.l2terrain/

├── crypto/java -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps -v

│   └── L2Decryptor.java      # XOR decryption for Ver 111/121+

├── extractors/```Extract all terrain tiles to a heightmaps folder:

│   ├── Extractor.java        # Extractor interface

│   └── HeightmapExtractor.java```bash

├── model/

│   ├── TerrainTile.java      # Single tile data### CLI Optionsjava -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps

│   └── TileCoordinates.java  # Grid coordinate parsing

└── L2TerrainExtractor.java   # CLI entry point```

```

```

## 🔮 Future Plans

Usage: l2terrain [-hVv] [-o=<output>] [-p=<pattern>] <inputDir>Verbose mode to see each tile extracted:

- Splatmap extraction (terrain texture blending)

- Texture layer extraction```bash



## 🤝 ContributingExtract terrain heightmaps from Lineage 2 packagesjava -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps -v



Contributions are welcome! Please feel free to submit a Pull Request.```



## 📄 LicenseParameters:



This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.      <inputDir>          Input directory containing .utx terrain packages## 🔧 How It Works



## 🙏 Credits



- Includes [unreal-package-lib](https://github.com/shrimpza/unreal-package-lib) by shrimpza (MIT License) for Unreal package parsingOptions:### Architecture

- Uses [picocli](https://picocli.info/) for CLI parsing

  -o, --output=<output>   Output directory (default: current directory)

## ⚠️ Disclaimer

  -p, --pattern=<pattern> File pattern to match (default: t_*_*.utx)- **Extractors (`Extractor`)** - Handle different data formats

This tool was **vibe coded** using **GitHub Copilot** by an author who freely admits to reverse engineering L2 file formats through trial and error rather than any actual documentation.

  -v, --verbose           Verbose output  - *Current:* `HeightmapExtractor` (G16 textures)

  -h, --help              Show this help message and exit.  - *Potential:* Splatmap extractor, texture layer extractor

  -V, --version           Print version information and exit.  

```- **Crypto (`L2Decryptor`)** - Handle package encryption

  - *Current:* Ver 111 (fixed key) and Ver 121+ (filename-derived key) XOR decryption

## 🔧 How It Works  - *Potential:* Other L2 encryption versions



### Architecture### Technical Details



- **Extractors (`Extractor`)** - Handle different data formats#### Encryption

  - *Current:* `HeightmapExtractor` (G16 textures)Lineage 2 encrypted packages have a 28-byte UTF-16LE header (`Lineage2VerXXX`) followed by XOR-encrypted data:

  - *Potential:* Splatmap extractor, texture layer extractor- **Ver 111**: Fixed XOR key `0xAC`

  - **Ver 121+**: Key derived from filename: `sum(lowercase filename characters) & 0xFF`

- **Crypto (`L2Decryptor`)** - Handle package encryption

  - *Current:* Ver 111 (fixed key) and Ver 121+ (filename-derived key) XOR decryption#### G16 Format

  - *Potential:* Other L2 encryption versionsHeightmaps are stored as G16 textures (raw 16-bit grayscale):

- 256×256 pixels per tile

### Technical Details- Little-endian unsigned 16-bit values

- Data follows marker pattern `00 40 80 10`

#### Encryption

#### Tile Naming

Lineage 2 encrypted packages have a 28-byte UTF-16LE header (`Lineage2VerXXX`) followed by XOR-encrypted data:Terrain tiles use naming convention `t_XX_YY.utx` where XX and YY are grid coordinates.



| Version | Key Derivation |## 📁 Project Structure

|---------|----------------|

| Ver 111 | Fixed XOR key `0xAC` |```

| Ver 121+ | `sum(lowercase filename characters) & 0xFF` |io.github.l2terrain/

├── crypto/

#### G16 Format│   └── L2Decryptor.java      # XOR decryption for Ver 111/121+

├── extractors/

Heightmaps are stored as G16 textures (raw 16-bit grayscale):│   ├── Extractor.java        # Extractor interface

- 256×256 pixels per tile│   └── HeightmapExtractor.java

- Little-endian unsigned 16-bit values├── model/

- Data follows marker pattern `00 40 80 10`│   ├── TerrainTile.java      # Single tile data

│   └── TileCoordinates.java  # Grid coordinate parsing

#### Tile Naming└── L2TerrainExtractor.java   # CLI entry point

```

Terrain tiles use naming convention `t_XX_YY.utx` where XX and YY are grid coordinates.

## 🔮 Future Plans

## 📁 Project Structure

- Splatmap extraction (terrain texture blending)

```- Texture layer extraction

io.github.l2terrain/

├── crypto/## 🤝 Contributing

│   └── L2Decryptor.java      # XOR decryption for Ver 111/121+

├── extractors/Contributions are welcome! Please feel free to submit a Pull Request.

│   ├── Extractor.java        # Extractor interface

│   └── HeightmapExtractor.java## 📄 License

├── model/

│   ├── TerrainTile.java      # Single tile dataThis project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

│   └── TileCoordinates.java  # Grid coordinate parsing

└── L2TerrainExtractor.java   # CLI entry point## 🙏 Credits

```

- Includes [unreal-package-lib](https://github.com/shrimpza/unreal-package-lib) by shrimpza (MIT License) for Unreal package parsing

## 🔮 Future Plans- Uses [picocli](https://picocli.info/) for CLI parsing



- Splatmap extraction (terrain texture blending)## ⚠️ Disclaimer

- Texture layer extraction

This tool was vibe coded using GitHub Copilot by an author who freely admits to reverse engineering L2 file formats through trial and error rather than any actual documentation.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Credits

- Includes [unreal-package-lib](https://github.com/shrimpza/unreal-package-lib) by shrimpza (MIT License) for Unreal package parsing
- Uses [picocli](https://picocli.info/) for CLI parsing

## ⚠️ Disclaimer

This tool was **vibe coded** using **GitHub Copilot** by an author who freely admits to reverse engineering L2 file formats through trial and error rather than any actual documentation.
