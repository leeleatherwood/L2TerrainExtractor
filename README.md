# L2TerrainExtractor

> **Lineage 2 Terrain Heightmap Extractor**

Extract G16 heightmap textures from encrypted Lineage 2 `.utx` terrain packages. Features automatic tile detection, XOR decryption for Ver 111/121+ packages, and outputs both PNG and RAW formats for each tile.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java 17+](https://img.shields.io/badge/Java-17+-ED8B00.svg)](https://openjdk.org/)
[![Gradle 8.5](https://img.shields.io/badge/Gradle-8.5-02303A.svg)](https://gradle.org/)

![L2 Heightmaps](l2_heightmaps.png)

## Features

- **Automatic Decryption** - Handles Lineage 2 Ver 111 and Ver 121+ XOR encryption
- **Smart Tile Detection** - Automatically finds G16 data using marker pattern detection
- **Dual Output Formats** - PNG (8-bit normalized) for viewing, RAW (16-bit) for terrain editors
- **Fast Processing** - Processes 150+ tiles in seconds

## Requirements

- **Java 17+**

## Quick Start

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/L2TerrainExtractor.git
cd L2TerrainExtractor
```

2. Build the project:
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

### Examples

Extract all terrain tiles to a heightmaps folder:
```bash
java -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps
```

Verbose mode to see each tile extracted:
```bash
java -jar l2terrain-1.0.0-all.jar "C:\L2\textures" -o heightmaps -v
```

## CLI Options

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

## How It Works

### Architecture

- **Extractors** - Handle different data formats (HeightmapExtractor for G16 textures)
- **Crypto** - Handle package encryption (Ver 111 fixed key, Ver 121+ filename-derived key)

### Encryption

Lineage 2 encrypted packages have a 28-byte UTF-16LE header followed by XOR-encrypted data:

| Version | Key Derivation |
|---------|----------------|
| Ver 111 | Fixed XOR key 0xAC |
| Ver 121+ | sum(lowercase filename characters) & 0xFF |

### G16 Format

Heightmaps are stored as G16 textures (raw 16-bit grayscale):
- 256x256 pixels per tile
- Little-endian unsigned 16-bit values
- Data follows marker pattern 00 40 80 10

## Project Structure

```
io.github.l2terrain/
├── crypto/
│   └── L2Decryptor.java
├── extractors/
│   ├── Extractor.java
│   └── HeightmapExtractor.java
├── model/
│   ├── TerrainTile.java
│   └── TileCoordinates.java
└── L2TerrainExtractor.java
```

## Future Plans

- Splatmap extraction (terrain texture blending)
- Texture layer extraction

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Credits

- Includes [unreal-package-lib](https://github.com/shrimpza/unreal-package-lib) by shrimpza (MIT License) for Unreal package parsing
- Uses [picocli](https://picocli.info/) for CLI parsing

## Disclaimer

This tool was **vibe coded** using **GitHub Copilot** by an author who freely admits to reverse engineering L2 file formats through trial and error rather than any actual documentation.
