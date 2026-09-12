package volume

import (
	"github.com/jotfs/fastcdc-go"
	"github.com/opencontainers/go-digest"
)

const (
	ArtifactType    = "application/vnd.sls.volume.v1"
	ConfigMediaType = "application/vnd.sls.volume.config.v1+json"

	PackFormat = "tar"
)

// Manifest is the SLS volume manifest stored as an OCI config blob
// it contains information needed to reconstruct and mount the volume
type Manifest struct {
	SchemaVersion    int            `json:"schemaVersion"`
	MediaType        string         `json:"mediaType"`
	Format           string         `json:"format"`
	Compression      string         `json:"compression"`
	Chunking         ChunkingConfig `json:"chunking"`
	UncompressedSize int64          `json:"uncompressedSize"`
	Chunks           []ChunkMeta    `json:"chunks"`
	MountInfo        MountInfo      `json:"mountInfo"`
}

// MountInfo contains the default mount options
// these are set when the volume is built
// the blueprint may override these values
type MountInfo struct {
	target string
	mode   string
}

// ChunkingConfig contains the FastCDC parameters used to chunk the volume.
// These parameters are required to correctly reconstruct the volume.
type ChunkingConfig struct {
	MinSize       int  `json:"minSize"`
	AverageSize   int  `json:"averageSize"`
	MaxSize       int  `json:"maxSize"`
	Normalization int  `json:"normalization"`
	DisableNorm   bool `json:"disableNormalization,omitempty"`
}

// ChunkMeta contains metadata for a single volume chunk.
type ChunkMeta struct {
	Index              int           `json:"index"`
	Offset             int64         `json:"offset"`
	UncompressedSize   int           `json:"uncompressedSize"`
	UncompressedDigest digest.Digest `json:"uncompressedDigest"`
	CompressedSize     int64         `json:"compressedSize"`
	CompressedDigest   digest.Digest `json:"compressedDigest"`
	MediaType          string        `json:"mediaType"`
}

type CompressedChunk struct {
	ChunkMeta
	Data []byte
}

func NewManifest(chunking fastcdc.Options, chunks []CompressedChunk) Manifest {
	return Manifest{}
}
