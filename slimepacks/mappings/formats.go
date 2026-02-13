package mappings

import (
	_ "embed"
	"encoding/json"
	"strconv"
	"strings"

	"emperror.dev/errors"
)

//go:embed pack_formats.json
var PackFormatJSON []byte

type PackFormatInfo struct {
	Format   int    `json:"format"`
	Versions string `json:"versions"`
}

type FormatsTable struct {
	Formats []PackFormatInfo
}

func LoadPackFormats() (*FormatsTable, error) {
	var table []PackFormatInfo
	err := json.Unmarshal(PackFormatJSON, &table)
	if err != nil {
		return nil, err
	}

	formats := &FormatsTable{
		Formats: table,
	}

	return formats, err
}

// FindClosestPackFormat returns the closest PackFormatInfo whose Format
// is >= the given packFormat. If none are >=, it returns the highest Format.
func (formatsTable *FormatsTable) FindClosestPackFormat(packFormat int) PackFormatInfo {
	table := formatsTable.Formats
	var best *PackFormatInfo

	for i := range table {
		entry := &table[i]

		// Exact match: return immediately
		if entry.Format == packFormat {
			return *entry
		}

		// Candidate: must be >= packFormat
		if entry.Format >= packFormat {
			// If we haven't chosen one yet, or this is smaller than current best
			if best == nil || entry.Format < best.Format {
				best = entry
			}
		}
	}

	// If nothing was >= packFormat, return the highest entry
	if best == nil {
		highest := table[0]
		for _, e := range table {
			if e.Format > highest.Format {
				highest = e
			}
		}
		return highest
	}

	return *best
}

// GetPackFormatFromVersion returns the pack format that matches the version.
// If none is found, it returns the newest pack format.
func (formatsTable *FormatsTable) GetPackFormatFromVersion(version string) (int, error) {
	if len(formatsTable.Formats) == 0 {
		return 0, errors.New("no pack formats defined")
	}

	requestedVersion, err := parseVersion(version)
	if err != nil {
		return 0, err
	}

	for _, formatInfo := range formatsTable.Formats {
		lower, upper, err := parseVersionRange(formatInfo.Versions)
		if err != nil {
			return 0, err
		}

		if compareVersions(requestedVersion, lower) < 0 {
			continue
		}
		if compareVersions(requestedVersion, upper) > 0 {
			continue
		}
		return formatInfo.Format, nil
	}

	return formatsTable.Formats[len(formatsTable.Formats)-1].Format, nil
}

// GetVersionRangeFromPackFormat returns the version range that corresponds to the given pack format.
func (formatsTable *FormatsTable) GetVersionRangeFromPackFormat(format int) (string, error) {
	// Iterate through all formats to find the matching format
	for _, formatInfo := range formatsTable.Formats {
		if formatInfo.Format == format {
			return formatInfo.Versions, nil
		}
	}

	// If no matching format is found, return the newest (highest) format's range
	if len(formatsTable.Formats) == 0 {
		return "", errors.New("no formats available")
	}

	return formatsTable.Formats[len(formatsTable.Formats)-1].Versions, nil
}

// GetVersionFromFormat returns the highest version that corresponds to the given pack format.
// If the versions field is a range (e.g. "1.20–1.20.4"), it returns the upper bound.
func (formatsTable *FormatsTable) GetVersionFromFormat(format int) (string, error) {
	var highest *PackFormatInfo

	extractUpperBound := func(versions string) string {
		versions = strings.TrimSpace(versions)

		if parts := strings.SplitN(versions, "–", 2); len(parts) == 2 {
			return strings.TrimSpace(parts[1])
		}

		if parts := strings.SplitN(versions, "-", 2); len(parts) == 2 {
			return strings.TrimSpace(parts[1])
		}

		return versions
	}

	for i := range formatsTable.Formats {
		formatInfo := &formatsTable.Formats[i]

		if highest == nil || formatInfo.Format > highest.Format {
			highest = formatInfo
		}

		if formatInfo.Format == format {
			return extractUpperBound(formatInfo.Versions), nil
		}
	}

	if highest == nil {
		return "", errors.New("no formats available")
	}

	return extractUpperBound(highest.Versions), nil
}

func parseVersionRange(rangeStr string) ([]int, []int, error) {
	rangeStr = strings.TrimSpace(rangeStr)
	if rangeStr == "" {
		return nil, nil, errors.New("empty version range")
	}

	var lowerStr, upperStr string

	if parts := strings.SplitN(rangeStr, "–", 2); len(parts) == 2 {
		lowerStr = parts[0]
		upperStr = parts[1]
	} else if parts := strings.SplitN(rangeStr, "-", 2); len(parts) == 2 {
		lowerStr = parts[0]
		upperStr = parts[1]
	} else {
		lowerStr = rangeStr
		upperStr = rangeStr
	}

	lower, err := parseVersion(lowerStr)
	if err != nil {
		return nil, nil, err
	}

	upper, err := parseVersion(upperStr)
	if err != nil {
		return nil, nil, err
	}

	return lower, upper, nil
}

func parseVersion(version string) ([]int, error) {
	version = strings.TrimSpace(version)
	if version == "" {
		return nil, errors.New("empty version string")
	}

	parts := strings.Split(version, ".")
	result := make([]int, len(parts))

	for i, part := range parts {
		value, err := strconv.Atoi(strings.TrimSpace(part))
		if err != nil {
			return nil, errors.Errorf("invalid version segment %q", part)
		}
		result[i] = value
	}

	return result, nil
}

func compareVersions(a, b []int) int {
	maxLen := len(a)
	if len(b) > maxLen {
		maxLen = len(b)
	}

	for i := 0; i < maxLen; i++ {
		var ai, bi int
		if i < len(a) {
			ai = a[i]
		}
		if i < len(b) {
			bi = b[i]
		}

		if ai < bi {
			return -1
		}
		if ai > bi {
			return 1
		}
	}

	return 0
}
