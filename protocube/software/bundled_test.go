package software

import (
	"os"
	"path/filepath"
	"testing"

	"gopkg.in/yaml.v3"
)

func TestBundledSoftwareDefinitionsLoad(t *testing.T) {
	for _, id := range []string{"paper", "spigot"} {
		sw := loadBundledSoftwareForTest(t, id)
		if !sw.InstallScript.Warmup {
			t.Fatalf("expected bundled %s software to use daemon warmup", id)
		}
		if sw.InstallScript.PostWarmupScript == "" {
			t.Fatalf("expected bundled %s software to define post-warmup cleanup", id)
		}
	}
}

func loadBundledSoftwareForTest(t *testing.T, id string) *Software {
	t.Helper()

	data, err := os.ReadFile(filepath.Join("..", "..", "software", id+".yml"))
	if err != nil {
		t.Fatal(err)
	}

	var cfg config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		t.Fatal(err)
	}
	if cfg.Software.Id != id {
		t.Fatalf("expected software id %q, got %q", id, cfg.Software.Id)
	}
	return &cfg.Software
}
