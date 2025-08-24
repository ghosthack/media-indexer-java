# Media Indexer

A cross-platform media indexing solution designed to traverse specified directory roots, extract metadata, compute hashes, generate thumbnails, and produce browsable HTML index pages with a query-ready SQLite database.

## Features

- **Cross-platform**: Runs on macOS, Windows, and Linux
- **Incremental operations**: Only processes new or changed files
- **Duplicate detection**: Uses both quick and content hashes to identify duplicates
- **Thumbnail generation**: Creates both full thumbnails and mini thumbnails
- **HTML index**: Generates paginated HTML galleries with embedded thumbnails
- **Configurable**: YAML-based configuration system
- **Resilient**: Handles filesystem loops, symlinks, and junction points safely

## Supported Formats

- **Images**: JPEG, PNG, BMP, GIF, WebP, HEIC, HEIF, TIFF, DNG, RAW, CR2, NEF, ARW
- **Videos**: MP4, MOV, AVI, MKV, WebM, M4V

## Building

```bash
mvn clean package
```

This creates an executable JAR file in the `target/` directory.

## Usage

### 1. Bootstrap Configuration

Create a default configuration file:

```bash
java -jar target/media-indexer-1.0.0.jar --bootstrap
```

This creates `media-indexer-config.yaml` with default settings.

### 2. Add Scan Roots

Add directories to scan for media files:

```bash
java -jar target/media-indexer-1.0.0.jar --add-root "/path/to/photos"
java -jar target/media-indexer-1.0.0.jar --add-root "/path/to/videos"
```

### 3. Scan Files

Perform a fast scan (metadata + quick hashes):

```bash
java -jar target/media-indexer-1.0.0.jar --fast
```

Or perform a full scan (metadata + quick hashes + content hashes):

```bash
java -jar target/media-indexer-1.0.0.jar --scan-full
```

### 4. Generate Content Hashes

Generate full content hashes for duplicate detection:

```bash
java -jar target/media-indexer-1.0.0.jar --content-hash
```

### 5. Create Thumbnails

Generate thumbnails and mini thumbnails:

```bash
java -jar target/media-indexer-1.0.0.jar --thumbnails
```

### 6. Generate HTML Index

Create browsable HTML pages:

```bash
java -jar target/media-indexer-1.0.0.jar --html
```

## Configuration

The configuration file `media-indexer-config.yaml` supports the following options:

```yaml
# Database and output paths
databasePath: "media-index.db"
thumbnailOutputDir: "output/thumbnails"
htmlOutputDir: "output/html"

# Directories to scan
scanRoots:
  - "/Users/username/Pictures"
  - "/Users/username/Videos"

# Thumbnail settings
thumbnail:
  maxDimension: 512
  quality: 0.85
  format: "JPEG"
  respectExifOrientation: true

# Mini thumbnail settings
miniThumbnail:
  maxHeight: 100
  quality: 0.85
  format: "JPEG"
  respectExifOrientation: true

# HTML output settings
html:
  maxPageSizeBytes: 209715200  # 200MB
  indexFileName: "index.html"

# Hashing settings
hashing:
  contentHashAlgorithm: "SHA-256"  # or "FNV-1"

# Performance settings
performance:
  thumbnailThreads: 4
  bufferPoolSize: 10
  maxMemoryMB: 1024
```

## Command Line Options

| Option | Description |
|--------|-------------|
| `--bootstrap` | Create a default configuration file |
| `--add-root PATH` | Add a directory to scan |
| `--fast` | Scan filesystem using quick hashes |
| `--content-hash` | Generate full content hashes |
| `--scan-full` | Full scan with both hash types |
| `--thumbnails` | Generate thumbnails and mini thumbnails |
| `--html` | Generate HTML index pages |
| `-c, --config PATH` | Specify configuration file path |
| `-h, --help` | Show help message |
| `-V, --version` | Show version information |

## Workflow

A typical workflow might look like:

1. **Initial setup**:
   ```bash
   java -jar media-indexer.jar --bootstrap
   java -jar media-indexer.jar --add-root "/Users/john/Photos"
   ```

2. **Full processing**:
   ```bash
   java -jar media-indexer.jar --scan-full
   java -jar media-indexer.jar --thumbnails
   java -jar media-indexer.jar --html
   ```

3. **Incremental updates** (after adding new photos):
   ```bash
   java -jar media-indexer.jar --fast
   java -jar media-indexer.jar --thumbnails
   java -jar media-indexer.jar --html
   ```

## Output

The system generates:

- **SQLite database**: `media-index.db` (or configured path)
- **Thumbnails**: Individual JPEG files in `output/thumbnails/`
- **HTML index**: Paginated HTML files in `output/html/`
- **Log file**: `media-indexer.log`

## Performance Considerations

- Thumbnail generation is memory-intensive; adjust `maxMemoryMB` as needed
- Use quick hashes for fast duplicate detection across large collections
- Content hashes provide definitive duplicate detection but are slower
- HTML pagination prevents browser memory issues with large collections

## Platform-Specific Notes

- **macOS**: Handles APFS snapshots and hardlinks safely
- **Windows**: Supports NTFS junctions and Unicode paths
- **Linux**: Works with various filesystems and symbolic links

## Requirements

- Java 21 or higher
- Sufficient disk space for thumbnails and HTML output
- Memory allocation appropriate for your media collection size