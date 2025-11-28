# L2TerrainExtractor 🗺️
Lineage 2 Terrain Heightmap Extractor

Extract G16 heightmap textures from encrypted Lineage 2 `.utx` terrain packages. Features automatic tile detection, XOR decryption for Ver 111/121+ packages, and outputs both PNG and RAW formats for each tile.

![L2 Heightmaps](l2_heightmaps.png)

![License](https://img.shields.io/badge/license-MIT-blue.svg) ![Java](https://img.shields.io/badge/Java-17+-ED8B00) ![Gradle](https://img.shields.io/badge/Gradle-8.5-02303A)

## ✨ Features

- 🔓 **Automatic Decryption** - Handles Lineage 2 Ver 111 and Ver 121+ XOR encryption
- 🧩 **Smart Tile Detection** - Automatically finds G16 data using marker pattern detection
- 📊 **Dual Output Formats** - PNG (8-bit normalized) for viewing, RAW (16-bit) for terrain editors
- ⚡ **Fast Processing** - Processes 150+ tiles in seconds

## 📋 Requirements

- Java 17 or later

## 🚀 Quick Start

### Building

```bash
./gradlew build
```

This creates a fat JAR at `build/libs/l2terrain-1.0.0-all.jar` with all dependencies included.

### Basic Usage

Extract heightmaps from a directory of `.utx` terrain files:

```bash
java -jar l2terrain-1.0.0-all.jar <input_directory> -o <output_directory>
```

Each tile produces two files: `XX_YY_heightmap.png` and `XX_YY_heightmap.raw`

### Options

```
Usage: l2terrain [-hVv] [-o=<output>] [-p=<pattern>] <inputDir>

Extract terrain heightmaps from Lineage 2 packages

Parameters:
      <inputDir>          Input directory containing .utx terrain packages

Options:
  -o, --output=<output>   Output directory (default: current directory)
  -p, --pattern=<pattern> File pattern to match (default: t_*_*.utx)
  -v, --verbose           Verbose output
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.
```

### Examples

Extract all terrain tiles to a heightmaps folder:
```bash
java -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps
```

Verbose mode to see each tile extracted:
```bash
java -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps -v
```

## 🔧 How It Works

### Architecture

- **Extractors (`Extractor`)** - Handle different data formats
  - *Current:* `HeightmapExtractor` (G16 textures)
  - *Potential:* Splatmap extractor, texture layer extractor
  
- **Crypto (`L2Decryptor`)** - Handle package encryption
  - *Current:* Ver 111 (fixed key) and Ver 121+ (filename-derived key) XOR decryption
  - *Potential:* Other L2 encryption versions

### Technical Details

#### Encryption
Lineage 2 encrypted packages have a 28-byte UTF-16LE header (`Lineage2VerXXX`) followed by XOR-encrypted data:
- **Ver 111**: Fixed XOR key `0xAC`
- **Ver 121+**: Key derived from filename: `sum(lowercase filename characters) & 0xFF`

#### G16 Format
Heightmaps are stored as G16 textures (raw 16-bit grayscale):
- 256×256 pixels per tile
- Little-endian unsigned 16-bit values
- Data follows marker pattern `00 40 80 10`

#### Tile Naming
Terrain tiles use naming convention `t_XX_YY.utx` where XX and YY are grid coordinates.

## 📁 Project Structure

```
io.github.l2terrain/
├── crypto/
│   └── L2Decryptor.java      # XOR decryption for Ver 111/121+
├── extractors/
│   ├── Extractor.java        # Extractor interface
│   └── HeightmapExtractor.java
├── model/
│   ├── TerrainTile.java      # Single tile data
│   └── TileCoordinates.java  # Grid coordinate parsing
└── L2TerrainExtractor.java   # CLI entry point
```

## 🔮 Future Plans

- Splatmap extraction (terrain texture blending)
- Texture layer extraction

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Credits

- Includes [unreal-package-lib](https://github.com/shrimpza/unreal-package-lib) by shrimpza (MIT License) for Unreal package parsing
- Uses [picocli](https://picocli.info/) for CLI parsing

## ⚠️ Disclaimer

This tool was vibe coded using GitHub Copilot by an author who freely admits to reverse engineering L2 file formats through trial and error rather than any actual documentation.
