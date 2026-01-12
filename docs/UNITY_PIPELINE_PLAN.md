# L2 → Unity Automated Terrain Pipeline

## Overview

This document outlines the complete plan for automating the extraction and import of Lineage 2 terrain and static mesh assets into Unity. The goal is a fully automated pipeline that reconstructs L2 terrain tiles as Unity prefabs with all associated static meshes correctly positioned.

---

## Folder Structure & Naming Conventions

### Design Principles
1. **Keep L2 names** - Easier debugging, traceable to source assets
2. **Mirror L2 package structure** - Meshes organized by source `_S` package
3. **TGA format** - UModel native output, Unity imports directly (PNG conversion in L2AssetEnhancer)
4. **GLTF meshes + co-located textures** - Textures in same folder as mesh, deduplicated across packages
5. **Texture deduplication** - First occurrence wins, subsequent meshes reference via relative paths

### Extraction Output (L2MeshExtractor) - CURRENT

```
L2MeshExtractor/
└── output/
    ├── Meshes/                               # All mesh packages
    │   ├── field_deco_S/                     # One folder per package
    │   │   ├── Meshes/                       # Mesh files (GLTF + BIN)
    │   │   │   ├── dgstone01.gltf
    │   │   │   ├── dgstone01.bin
    │   │   │   └── ...
    │   │   ├── Textures/                     # Texture files (TGA)
    │   │   │   ├── dgstone1.tga
    │   │   │   ├── dgstone2.tga
    │   │   │   └── ...
    │   │   └── Materials/                    # Empty (for Unity-generated)
    │   │
    │   ├── Giran_Village_S/
    │   │   ├── Meshes/
    │   │   ├── Textures/
    │   │   └── Materials/
    │   └── .../
    │
    └── extraction_manifest.json              # Tracks texture locations for deduplication
```

**Folder Structure (Updated):**
- Each package has 3 subfolders: `Meshes/`, `Textures/`, `Materials/`
- GLTF files reference textures via `../Textures/filename.tga` (same package)
- Cross-package references: `../../OtherPackage/Textures/filename.tga`
- Materials folder is empty (Unity will generate materials from GLTF)

### Unity Project Structure (Current)

```
UnityProject/
└── Assets/
    └── L2/
        ├── Meshes/                           # Copy from L2MeshExtractor/output/Meshes
        │   ├── field_deco_S/
        │   │   ├── Meshes/                   # GLTF + BIN files
        │   │   │   ├── dgstone01.gltf
        │   │   │   └── dgstone01.bin
        │   │   ├── Textures/                 # TGA texture files
        │   │   │   ├── dgstone1.tga
        │   │   │   └── ...
        │   │   └── Materials/                # Unity generates materials here
        │   └── .../
        │
        └── Terrain/                          # From L2TerrainExtractor
            ├── TerrainTextures/              # Shared ground textures (339 total)
            │   ├── gus103.png                # Extracted from regional packages
            │   ├── gus103_normal.png         # Optional PBR (if enhanced)
            │   ├── gus103_hdrp_mask.png      # Optional PBR
            │   └── ...
            │
            ├── TerrainLayers/                # Shared Unity .terrainlayer assets
            │   ├── GUS103.terrainlayer       # References TerrainTextures/gus103.png
            │   ├── SL_S6.terrainlayer
            │   └── ...
            │
            └── Tiles/                        # Per-tile data + prefabs
                └── 17_21/
                    ├── heightmap.raw         # 16-bit RAW heightmap
                    ├── splatmap_0.png        # RGBA = layers 0-3
                    ├── splatmap_1.png        # RGBA = layers 4-7
                    ├── metadata.txt          # Layer → texture mapping
                    ├── staticmeshes.json     # Static mesh placements
                    ├── Tile_17_21.prefab     # Generated prefab (static meshes)
                    └── Terrain_17_21.asset   # Unity Terrain Data (optional)
```

**Notes:**
- Unity imports TGA textures natively (no pre-conversion needed)
- L2AssetEnhancer can later convert TGA → PNG + upscale in-place
- Each package folder has Meshes/Textures/Materials subfolders
- Splatmaps are combined from grayscale layers → RGBA during import
- TerrainLayers are shared across tiles (texture "SL_S6" → one .terrainlayer)
- Tiles with >8 layers get additional splatmap_2.png, etc.

### GLTF Texture Injection (L2MeshExtractor)

L2MeshExtractor injects texture references into GLTF files. The GLTF files include:

```json
{
  "materials": [
    {
      "name": "dgstone1",
      "pbrMetallicRoughness": {
        "baseColorFactor": [1.0, 1.0, 1.0, 1.0],
        "metallicFactor": 0.1,
        "roughnessFactor": 0.5,
        "baseColorTexture": { "index": 0 }
      }
    }
  ],
  "images": [
    { "uri": "dgstone1.tga" }                    // Local file (same folder)
    // OR: { "uri": "../OtherPackage/shared.tga" }  // Cross-package reference
  ],
  "textures": [
    { "sampler": 0, "source": 0 }
  ],
  "samplers": [
    { "magFilter": 9729, "minFilter": 9987, "wrapS": 10497, "wrapT": 10497 }
  ]
}
```

**Material-to-Texture Matching:**
- Material names typically match texture names (e.g., `dgstone1` → `dgstone1.tga`)
- L2MeshExtractor uses fuzzy matching for suffixes (`_1`, `_Mat`, `_sh`, `_mask`)
- Multi-material meshes get multiple textures injected

**Texture Deduplication:**
- First occurrence of a texture is saved to its package folder
- Subsequent references use relative paths (`../FirstPackage/texture.tga`)
- Tracked in `extraction_manifest.json`

Unity auto-imports GLTF with textures correctly assigned to materials.

### Naming Conventions

| Asset Type | L2 Source | Extracted Name | Unity Name |
|------------|-----------|----------------|------------|
| **Mesh Package** | `field_deco_S.usx` | `Meshes/field_deco_S/` | `L2/Meshes/field_deco_S/` |
| **Static Mesh** | `dgstone01` | `dgstone01.gltf` + `.bin` | Same (auto-imported) |
| **Texture** | `dgstone1` | `dgstone1.tga` | Same (TGA → PNG via L2AssetEnhancer) |
| **Material** | Embedded in GLTF | Auto-generated | Unity creates from GLTF |
| **Tile Prefab** | N/A | N/A | `Tile_17_24.prefab` |
| **staticmeshes.json** | Tile data | `output/XX_YY/staticmeshes.json` | `L2/Tiles/XX_YY/staticmeshes.json` |

### Texture Processing

**Phase 1 (Basic) - Current:**
- `{texture_name}.tga` - Original L2 texture (Unity imports natively)

**Phase 2 (L2AssetEnhancer - Optional Enhancement):**
Run L2AssetEnhancer on assets already in Unity:
- `{texture_name}.png` - TGA → PNG conversion + 4x AI upscaling (Real-ESRGAN)
- `{texture_name}_normal.png` - Normal map (generated from diffuse)
- `{texture_name}_hdrp_mask.png` - HDRP Mask texture:
  - **R** = Metallic (usually 0 for L2 assets)
  - **G** = Ambient Occlusion
  - **B** = Detail mask (usually 1)
  - **A** = Smoothness (inverted roughness)
- `{texture_name}_height.png` - Height/displacement (optional)

**Geometry Enhancement (Optional):**
- Edge beveling (Blender)
- Subdivision surface (Blender)
- Exports updated GLTF in-place

### Coordinate System Handling

| Aspect | L2 (Unreal 2) | Unity | Conversion |
|--------|---------------|-------|------------|
| **Up axis** | Z-up | Y-up | Swap Y↔Z |
| **Scale** | Unreal Units (~1cm) | Meters | ×0.01 |
| **Rotation** | Pitch/Yaw/Roll (degrees) | Quaternion | Euler→Quat + axis remap |

**Decision**: Handle coordinate conversion during **prefab generation** in Unity (not during mesh export). This keeps GLTF files standard and allows adjustment if needed.

---

## Current State

### Completed Components

| Component | Technology | Status | Outputs |
|-----------|------------|--------|---------|
| **L2TerrainExtractor** | Java | ✅ Complete | Heightmaps, splatmaps, detail maps, terrain textures, metadata, static mesh placements |
| **L2toUnity** | PowerShell | ✅ Working | Enhanced meshes + PBR textures (manual, with upscaling) |

### What L2TerrainExtractor Produces

```
output/
├── heightmaps/
│   ├── XX_YY_heightmap.png      # 16-bit grayscale heightmap
│   └── XX_YY_heightmap.raw      # Raw G16 data
├── splatmaps/
│   └── XX_YY_splatmapN_layerM.png  # Terrain blend weights
├── detailmaps/
│   └── XX_YY_detailmap_N.png    # Vegetation density maps
├── terraintextures/
│   └── texture_name.png         # Ground tiling textures
└── XX_YY/
    ├── metadata.json            # Terrain layer configuration
    └── staticmeshes.json        # Static mesh placements (NEW)
```

### Static Mesh Placement Data

Each tile's `staticmeshes.json` contains:
```json
{
  "tile": "22_22",
  "count": 1936,
  "staticMeshes": [
    {
      "name": "StaticMeshActor64",
      "mesh": "Giran_V_Plaza_Wall04",
      "package": "Giran_Village_S",
      "meshPath": "Giran_Village_S.Giran_V_Plaza_Wall04",
      "location": {"x": 80662.0, "y": 148618.0, "z": -3387.0},
      "rotation": {"pitch": 0.0, "yaw": -90.0, "roll": 0.0},
      "scale": {"uniform": 1.0, "x": 1.0, "y": 1.0, "z": 1.0},
      "flags": {
        "hidden": false,
        "shadowCast": true,
        "collideActors": false,
        "blockActors": false,
        "blockPlayers": false
      }
    }
  ]
}
```

---

## Pipeline Architecture

### Updated Architecture (Current State)

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                    L2 → Unity Pipeline (Current State)                         │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│  STAGE 1: EXTRACTION ✅ COMPLETE                                               │
│  ┌─────────────────────────┐    ┌─────────────────────────┐                    │
│  │  L2TerrainExtractor     │    │  L2MeshExtractor        │                    │
│  │       (Java)            │    │  (Java + UModel CLI)    │                    │
│  │                         │    │                         │                    │
│  │ • Heightmaps (.raw)     │    │ • 172 packages extracted│                    │
│  │ • Splatmaps (.png)      │    │ • 15,690 GLTF meshes    │                    │
│  │ • Terrain textures      │    │ • 21,128 TGA textures   │                    │
│  │ • staticmeshes.json     │    │ • Texture deduplication │                    │
│  │                         │    │ • GLTF texture injection│                    │
│  └──────────┬──────────────┘    └──────────┬──────────────┘                    │
│             │                              │                                   │
│             ▼                              ▼                                   │
│  output/XX_YY/                  output/Meshes/{Package}/                       │
│    staticmeshes.json              *.gltf, *.bin, *.tga                         │
│    metadata.json                                                               │
│    heightmap.raw                                                               │
│                                                                                │
│  STAGE 2: UNITY INTEGRATION 🔜 NEXT                                            │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │  Unity Project (Assets/L2/)                                             │   │
│  │  ├── Meshes/{Package}/*.gltf        ◄── Copy from L2MeshExtractor      │   │
│  │  ├── Terrain/Heightmaps/*.raw       ◄── Copy from L2TerrainExtractor   │   │
│  │  └── Tiles/{XX_YY}/staticmeshes.json◄── Copy from L2TerrainExtractor   │   │
│  │                                                                         │   │
│  │  Editor Scripts:                                                        │   │
│  │  • L2TilePrefabBuilder.cs  - Build prefabs from staticmeshes.json      │   │
│  │  • L2CoordinateConverter.cs - Z-up → Y-up, ×0.01 scale                 │   │
│  │                                                                         │   │
│  │  Collision: Use visual mesh (L2 meshes are low-poly enough)            │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                │
│  STAGE 3: ENHANCEMENT (Optional - L2AssetEnhancer) 📋 PLANNED                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │  L2AssetEnhancer (Python)                                               │   │
│  │  Point at GLTF already in Unity → enhance in-place:                     │   │
│  │                                                                         │   │
│  │  • TGA → PNG conversion                                                 │   │
│  │  • AI upscaling (Real-ESRGAN 4x)                                        │   │
│  │  • PBR generation (normal, HDRP mask, height)                           │   │
│  │  • Geometry enhancement (beveling, subdivision)                         │   │
│  │                                                                         │   │
│  │  Works for: static meshes, characters, weapons, etc.                    │   │
│  │  Replaces: L2toUnity PowerShell scripts                                 │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                │
└────────────────────────────────────────────────────────────────────────────────┘
```

### Key Workflow Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Mesh format | **GLTF** | Unity auto-imports, UModel native output |
| Texture format | **PNG (max quality)** | Unity-native, lossless |
| Mesh names | **Keep L2 names** | Easier debugging |
| Texture organization | **Per-package (Textures/{pkg}/)** | Mirrors L2 structure, deduplicates |
| Coord conversion | **During prefab generation** | Keep GLTF files standard |
| Enhancement | **Optional stage** | Can use raw or enhanced assets |

---

## Components

### 1. L2MeshExtractor (Java + UModel) ✅ IMPLEMENTED

**Purpose**: Batch extract StaticMeshes and textures from L2 packages using UModel CLI.

**Location**: `d:\workspaces\L2MeshExtractor\`

**Architecture**:
```
L2MeshExtractor/
├── src/main/java/io/github/l2mesh/
│   ├── L2MeshExtractor.java        # CLI entry point - batch extraction
│   └── MeshPackageScanner.java     # Analyze staticmeshes.json files
├── tools/
│   └── umodel/
│       └── umodel_64.exe           # UModel CLI for extraction
├── output/
│   ├── Meshes/{Package}/           # GLTF meshes organized by package
│   └── Textures/{Package}/         # PNG textures organized by package
├── build.gradle
└── README.md
```

**Workflow**:
1. **Scan**: Parse L2TerrainExtractor's staticmeshes.json files
2. **Deduplicate**: Identify unique packages to extract
3. **Extract**: UModel batch export (GLTF + textures)
4. **Convert**: TGA → PNG (max quality)
5. **Organize**: Move to package-based folder structure
6. **Inject Textures**: Add images/textures/samplers arrays to GLTF, link materials to textures

**CLI Usage**:
```bash
# Extract meshes for all tiles
java -jar l2mesh-1.0.0-all.jar \
  --terrain-output "d:/workspaces/L2TerrainExtractor/output" \
  --l2-game-dir "d:/Lineage 2 Interlude" \
  --output output \
  --umodel "tools/umodel/umodel_64.exe"

# Extract for specific tile only
java -jar l2mesh-1.0.0-all.jar \
  --terrain-output "d:/workspaces/L2TerrainExtractor/output" \
  --l2-game-dir "d:/Lineage 2 Interlude" \
  --output output \
  --umodel "tools/umodel/umodel_64.exe" \
  --tile 16_24
```

**UModel Command Used**:
```bash
umodel_64.exe -path="d:/Lineage 2 Interlude/staticmeshes" -game=l2 -export "field_deco_S.usx" -gltf -png -out="output"
```

**Stats (from scan)**:
- 172 unique packages
- 6,394 total mesh references
- 108 tiles processed

---

### 2. Unity Editor Scripts (C#)

**Purpose**: Import extracted assets and generate tile prefabs.

**Location**: `Assets/Editor/L2Pipeline/`

**Components**:
```
Assets/Editor/L2Pipeline/
├── L2TilePrefabBuilder.cs      # Generate prefabs from staticmeshes.json
├── L2CoordinateConverter.cs    # Unreal → Unity coordinate conversion
└── L2AssetImporter.cs          # Batch import GLTF with correct settings
```

**Key Functions**:
- Read staticmeshes.json for each tile
- Instantiate mesh prefabs at correct positions
- Apply coordinate conversion (Z-up → Y-up, scale)
- Generate Tile_XX_YY.prefab

---

### Reference: StaticMesh Format (UE2)

UModel handles the complex UE2 StaticMesh format internally. Key structures:

```cpp
// From UEViewer source
class UStaticMesh : public UPrimitive {
    TArray<FStaticMeshMaterial> Materials;   // Material references
    TArray<FStaticMeshSection>  Sections;    // Per-material mesh sections
    FStaticMeshVertexStream VertexStream;    // Vertices (position + normal)
    TArray<FStaticMeshUVStream> UVStream;    // UV coordinates
    FRawIndexBuffer IndexStream1;            // Triangle indices
};
```

UModel exports this directly to GLTF 2.0 with:
- Mesh geometry (POSITION, NORMAL, TANGENT, TEXCOORD_0)
- PBR materials (baseColorFactor)
- Binary buffer (.bin file)

---

### Coordinate Conversion Reference

| Aspect | Lineage 2 (Unreal 2) | Unity |
|--------|---------------------|-------|
| Up axis | Z-up | Y-up |
| Forward | X | Z |
| Right | Y | X |
| Units | ~2cm (Unreal Units) | Meters |
| Rotation | 65536 = 360° | Degrees/Quaternion |

```csharp
// Unity C# coordinate conversion
const float UNREAL_TO_UNITY_SCALE = 0.01f;  // 1 UU ≈ 1cm

Vector3 UnrealToUnityPosition(float ueX, float ueY, float ueZ) {
    return new Vector3(ueY * UNREAL_TO_UNITY_SCALE, 
                       ueZ * UNREAL_TO_UNITY_SCALE, 
                       ueX * UNREAL_TO_UNITY_SCALE);
}
```

---

### 3. Unity Import Scripts (C#) - FUTURE

**Purpose**: Build terrain tiles and place static meshes from staticmeshes.json.

**Location**: Unity project `Assets/Editor/L2Pipeline/`

**Components**:
```
Assets/
├── Editor/
│   └── L2Pipeline/
│       ├── L2TilePrefabBuilder.cs    # Main editor window
│       ├── L2CoordinateConverter.cs  # Unreal → Unity transform
│       └── L2MeshPlacer.cs           # Instantiate meshes from JSON
└── L2/
    ├── Meshes/{Package}/*.gltf       # Drop-in from extraction
    ├── Textures/{Package}/*.png      # Drop-in from extraction
    ├── Terrain/Heightmaps/*.raw
    └── Tiles/{XX_YY}/
        ├── staticmeshes.json          # From L2TerrainExtractor
        └── Tile_XX_YY.prefab          # Generated prefab
```

#### Prefab Structure (Per Tile)

```
Tile_17_24 (Prefab)
├── Terrain_17_24 (Terrain component)
└── StaticMeshes (Empty parent)
    ├── Giran_V_Plaza_Wall04 (mesh instance)
    ├── Giran_V_Plaza_Wall03 (mesh instance)
    └── ... (mesh instances from staticmeshes.json)
```

#### Tile Building Process

```csharp
public class L2TilePrefabBuilder : EditorWindow
{
    void BuildTile(string tileId) {
        // 1. Load staticmeshes.json
        var data = JsonUtility.FromJson<StaticMeshesData>(jsonContent);
        
        // 2. Create tile root GameObject
        var tileRoot = new GameObject($"Tile_{tileId}");
        
        // 3. For each mesh placement
        foreach (var mesh in data.staticMeshes) {
            // Load GLTF as prefab
            var prefab = AssetDatabase.LoadAssetAtPath<GameObject>(
                $"Assets/L2/Meshes/{mesh.package}/{mesh.mesh}.gltf");
            
            // Instantiate with coordinate conversion
            var instance = PrefabUtility.InstantiatePrefab(prefab) as GameObject;
            instance.transform.position = UnrealToUnity(mesh.location);
            instance.transform.rotation = UnrealRotationToUnity(mesh.rotation);
            instance.transform.localScale = mesh.scale;
            instance.transform.SetParent(tileRoot.transform);
        }
        
        // 4. Save as prefab
        PrefabUtility.SaveAsPrefabAsset(tileRoot, $"Assets/L2/Tiles/{tileId}/Tile_{tileId}.prefab");
    }
}
```

---

## Data Flow

### Extraction Output (L2MeshExtractor)

```
L2MeshExtractor/output/
├── Meshes/
│   └── {Package}/
│       ├── {mesh}.gltf         # Mesh with material refs
│       └── {mesh}.bin          # Binary geometry data
├── Textures/
│   └── {Package}/
│       └── {texture}.png       # Converted from TGA
└── extraction_manifest.json    # What was extracted
```

### Unity Import (copy + generate prefabs)

```
UnityProject/Assets/L2/
├── Meshes/                     # Copy from L2MeshExtractor/output
├── Textures/                   # Copy from L2MeshExtractor/output
├── Terrain/                    # From L2TerrainExtractor
│   ├── Heightmaps/
│   └── Splatmaps/
└── Tiles/
    └── {XX_YY}/
        ├── staticmeshes.json   # From L2TerrainExtractor
        └── Tile_{XX_YY}.prefab # Generated by L2TilePrefabBuilder.cs
```

---

## Implementation Phases

### Phase 1: L2MeshExtractor ✅ COMPLETE
- [x] Create project structure (Java + Gradle)
- [x] Package analyzer (scan staticmeshes.json, map to .usx files)
- [x] UModel integration (batch GLTF extraction)
- [x] Finalize folder structure (Meshes/{pkg}/, Textures/{texturePackage}/)
- [x] GLTF texture injection (add images/textures/samplers arrays, link materials)
- [x] Named argument CLI (--terrain-output, --l2-game-dir, --output, --tile, --umodel)
- [x] Test extraction: tile 16_24 → 691 meshes, 716 textures from 4 packages

### Phase 2: Full Batch Extraction ✅ COMPLETE
- [x] Organized output (per-package folders)
- [x] TGA textures (UModel native format, kept for Unity import)
- [x] GLTF texture references (local paths to textures in same folder)
- [x] Extraction manifest generation
- [x] Run batch extraction for all 172 packages
- [x] Texture deduplication (first-occurrence tracking, relative path references)

**Extraction Stats:**
- 172 mesh packages processed
- 7,845 GLTF meshes
- 9,967 unique TGA textures
- 1,194 texture references reused (cross-package)
- Folder structure: `{Package}/Meshes/`, `{Package}/Textures/`, `{Package}/Materials/`

### Phase 3: Unity Editor Import (C#) 🔜 NEXT
- [ ] L2TilePrefabBuilder.cs - read staticmeshes.json
- [ ] L2CoordinateConverter.cs - Unreal → Unity transform
- [ ] Batch tile prefab generation
- [ ] Validate meshes import correctly with textures

### Phase 4: L2AssetEnhancer (Python) - FUTURE
**Purpose**: Optional "after the fact" enhancement of assets already in Unity.

Point at a GLTF in Unity → automatically enhance:
- TGA → PNG conversion
- AI upscaling (Real-ESRGAN 4x)
- PBR texture generation (normal, HDRP mask, height)
- Geometry enhancement (edge beveling, subdivision)

**Key Design Decisions:**
- Works on assets **already imported into Unity** (not pre-import)
- Operates on GLTF files in Unity project folder
- Supports static meshes, characters, weapons, etc.
- Replaces L2toUnity PowerShell scripts with unified Python tool

**Proposed Architecture:**
```
L2AssetEnhancer/
├── l2enhance/
│   ├── __init__.py
│   ├── cli.py                    # Command-line interface
│   ├── pipeline.py               # Main pipeline orchestrator
│   ├── enhancers/
│   │   ├── texture_upscaler.py   # Real-ESRGAN 4x + TGA→PNG
│   │   ├── pbr_generator.py      # Normal, HDRP mask, height maps
│   │   └── mesh_enhancer.py      # Blender beveling, subdivision
│   └── output/
│       └── gltf_builder.py       # Final GLTF assembly
├── requirements.txt              # Real-ESRGAN, Pillow, etc.
└── README.md
```

**Usage Examples:**
```bash
# Enhance a single mesh in Unity project
python -m l2enhance --mesh "Assets/L2/Meshes/field_deco_S/dgstone01.gltf"

# Enhance all meshes in a package folder
python -m l2enhance --folder "Assets/L2/Meshes/Giran_Village_S/"

# Enhance with specific options
python -m l2enhance --mesh "mesh.gltf" \
    --upscale-factor 4 \
    --generate-pbr \
    --apply-beveling

# Textures only (no geometry changes)
python -m l2enhance --mesh "mesh.gltf" --textures-only
```

**Dependencies:**
- Python 3.10+
- Real-ESRGAN (PyTorch) - replaces Topaz Gigapixel
- Blender 4.5+ (headless) - PBR generation, geometry enhancement
- NVIDIA Texture Tools (optional) - GPU-accelerated normal maps
- Pillow, numpy - image processing

### Phase 5: Unity Runtime (Optional)
- [ ] Dynamic tile loader based on player position
- [ ] Tile streaming/unloading
- [ ] LOD integration for distant tiles

---

## Open Questions

### Resolved ✓
1. ~~StaticMesh format~~: Using UModel CLI - handles all UE2 variants
2. ~~Material complexity~~: GLTF PBR materials, Unity auto-imports
3. ~~Texture organization~~: Per-package structure (Textures/{pkg}/)
4. ~~Tile world coordinates~~: Convert from Unreal Units → Unity meters (×0.01 scale, Z-up → Y-up axis swap)
5. ~~Collision meshes~~: **Use visual mesh as collision** - L2 meshes are low-poly enough for modern physics engines
6. ~~TGA → PNG conversion~~: **Handled by L2AssetEnhancer** as part of optional upscaling workflow

### Remaining
7. **LOD meshes**: Should we set up Unity LOD groups from `_LOD` variants?

---

## Related Files

- **L2TerrainExtractor**: `d:\workspaces\L2TerrainExtractor\`
  - Static mesh placement extraction: `extractors/StaticMeshExtractor.java`
  - Output: `output/XX_YY/staticmeshes.json`

- **L2MeshExtractor**: `d:\workspaces\L2MeshExtractor\`
  - UModel wrapper for batch GLTF extraction
  - Package scanner: `MeshPackageScanner.java`
  - UModel location: `tools/umodel/umodel_64.exe`

- **L2toUnity** (reference): `d:\workspaces\Project Eternal\Tools\L2toUnity\`
  - GLTF texture URI pattern reference
  - Optional PBR texture generation

---

## Statistics (From Package Scan)

- **Unique packages**: 172 .usx files
- **Total mesh references**: 6,394 across 108 tiles
- **Tiles with meshes**: 108 tiles
- **Top packages by mesh count**:
  - field_deco_S: many meshes (dgstone, henge, etc.)
  - Giran_Village_S: city buildings
  - FX_E_S: effects/particles

---

## Next Steps

### Immediate (Phase 3 - Unity Integration)
1. **Copy extracted assets to Unity** - `L2MeshExtractor/output/Meshes/` → `Assets/L2/Meshes/`
2. **Copy terrain data** - `L2TerrainExtractor/output/` → `Assets/L2/Terrain/` and `Assets/L2/Tiles/`
3. **Create L2TilePrefabBuilder.cs** - Editor window to build prefabs from staticmeshes.json
4. **Create L2CoordinateConverter.cs** - Unreal → Unity coordinate conversion
5. **Validate imports** - Verify meshes display correctly with textures

### Future (Phase 4 - L2AssetEnhancer)
1. **Set up Python project** - After Unity folder structure is finalized
2. **Port Real-ESRGAN upscaling** - Replace Topaz Gigapixel with Python-native
3. **Port PBR generation** - Blender headless via Python
4. **Port geometry enhancement** - Blender beveling/subdivision
5. **Integrate with Unity project** - Point at `Assets/L2/Meshes/*/` folders
