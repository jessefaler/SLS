package mappings

import "testing"

func mustLoadFormats(t *testing.T) *FormatsTable {
	t.Helper()

	table, err := LoadPackFormats()
	if err != nil {
		t.Fatalf("LoadPackFormats() unexpected error: %v", err)
	}

	return table
}

func TestFindClosestPackFormat(t *testing.T) {
	table := mustLoadFormats(t)

	t.Run("exact match", func(t *testing.T) {
		format := table.FindClosestPackFormat(8)
		if format.Format != 8 || format.Versions != "1.18–1.18.2" {
			t.Fatalf("expected format 8, got %+v", format)
		}
	})

	t.Run("round up", func(t *testing.T) {
		format := table.FindClosestPackFormat(14)
		if format.Format != 15 {
			t.Fatalf("expected format 15, got %d", format.Format)
		}
	})

	t.Run("fallback highest", func(t *testing.T) {
		format := table.FindClosestPackFormat(999)
		if format.Format != 69 {
			t.Fatalf("expected highest format 69, got %d", format.Format)
		}
	})
}

func TestGetPackFormatFromVersion(t *testing.T) {
	table := mustLoadFormats(t)

	tests := []struct {
		name           string
		version        string
		expectedFormat int
	}{
		{
			name:           "single version entry",
			version:        "1.19.3",
			expectedFormat: 12,
		},
		{
			name:           "version range upper bound",
			version:        "1.20.1",
			expectedFormat: 15,
		},
		{
			name:           "version not found uses newest",
			version:        "9.9.9",
			expectedFormat: 69,
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			format, err := table.GetPackFormatFromVersion(tt.version)
			if err != nil {
				t.Fatalf("GetPackFormatFromVersion(%s) unexpected error: %v", tt.version, err)
			}

			if format != tt.expectedFormat {
				t.Fatalf("expected format %d, got %d", tt.expectedFormat, format)
			}
		})
	}
}

func TestGetVersionRangeFromPackFormat(t *testing.T) {
	table := mustLoadFormats(t)

	rangeStr, err := table.GetVersionRangeFromPackFormat(5)
	if err != nil {
		t.Fatalf("GetVersionRangeFromPackFormat() unexpected error: %v", err)
	}

	if rangeStr != "1.15–1.16.1" {
		t.Fatalf("expected range 1.15–1.16.1, got %s", rangeStr)
	}
}

func TestGetVersionFromFormat(t *testing.T) {
	table := mustLoadFormats(t)

	t.Run("range upper bound", func(t *testing.T) {
		version, err := table.GetVersionFromFormat(15)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if version != "1.20.1" {
			t.Fatalf("expected upper bound 1.20.1, got %s", version)
		}
	})

	t.Run("single version entry", func(t *testing.T) {
		version, err := table.GetVersionFromFormat(12)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if version != "1.19.3" {
			t.Fatalf("expected 1.19.3, got %s", version)
		}
	})

	t.Run("unknown format falls back to highest", func(t *testing.T) {
		version, err := table.GetVersionFromFormat(999)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if version != "1.21.10" {
			t.Fatalf("expected highest version 1.21.10, got %s", version)
		}
	})
}
