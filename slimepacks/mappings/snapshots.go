package mappings

import (
	"context"
	_ "embed"
	"encoding/json"
	"io"
	"net/http"

	"emperror.dev/errors"
)

//go:embed snapshots.json
var SnapshotsJSON []byte

// Snapshots maps snapshot / pre / rc identifiers to their corresponding
// release (major) version, e.g. "23w35a" -> "1.20.2".
type Snapshots map[string]string

// LoadSnapshotMappings loads the embedded snapshot-to-version mappings
// from snapshots.json and returns them as a Snapshots map.
func LoadSnapshotMappings() (*Snapshots, error) {
	var data map[string]string

	if err := json.Unmarshal(SnapshotsJSON, &data); err != nil {
		return nil, err
	}

	s := Snapshots(data)
	return &s, nil
}

// GetMajorVersion returns the major/release version for the given snapshot
// identifier and a boolean indicating whether a mapping was found.
func (s Snapshots) GetMajorVersion(snapshot string) (string, bool) {
	if s == nil {
		return "", false
	}
	v, ok := s[snapshot]
	return v, ok
}

const versionManifestURL = "https://piston-meta.mojang.com/mc/game/version_manifest.json"

type versionManifest struct {
	Versions []struct {
		ID   string `json:"id"`
		Type string `json:"type"`
	} `json:"versions"`
}

// FetchSnapshotMappings queries Mojang's version manifest and constructs a map
// of snapshot / prerelease identifiers to their eventual release versions.
// The manifest is ordered newest-first, so we track the most recent release
// encountered and assign it to subsequent snapshot entries until another
// release appears.
func FetchSnapshotMappings(ctx context.Context) (*Snapshots, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, versionManifestURL, nil)
	if err != nil {
		return nil, errors.Wrap(err, "creating manifest request")
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, errors.Wrap(err, "requesting manifest")
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 1024))
		return nil, errors.Errorf("manifest request failed: %s (%s)", resp.Status, string(body))
	}

	var manifest versionManifest
	if err := json.NewDecoder(resp.Body).Decode(&manifest); err != nil {
		return nil, errors.Wrap(err, "decoding manifest")
	}

	result := make(Snapshots)
	currentRelease := ""

	for _, entry := range manifest.Versions {
		switch entry.Type {
		case "release":
			currentRelease = entry.ID
		default:
			if currentRelease == "" {
				continue
			}
			result[entry.ID] = currentRelease
		}
	}

	return &result, nil
}
