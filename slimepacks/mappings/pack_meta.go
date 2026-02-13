package mappings

import (
	"encoding/json"
	"os"
)

type PackMeta struct {
	Pack struct {
		PackFormat  int    `json:"pack_format"`
		Description string `json:"description"`
	} `json:"pack"`
}

// LoadPackMeta reads a pack.mcmeta file and unmarshals it into a PackMeta struct.
func LoadPackMeta(path string) (*PackMeta, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	var meta PackMeta
	if err := json.Unmarshal(data, &meta); err != nil {
		return nil, err
	}

	return &meta, nil
}
