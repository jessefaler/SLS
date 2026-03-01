package software

import (
	"regexp"
	"strconv"
	"strings"

	"emperror.dev/errors"
)

// ImageForVersion returns the Docker image for the given server version,
// using the Software's mappings and images configuration.
//
// The mappings field is expected to look like:
//
//	mappings:
//	  - java_8:  "<=1.16.5"
//	  - java_16: ">=1.17 <=1.17.1"
//	  - java_17: ">=1.18 <=1.20.4"
//	  - java_21: ">=1.20.5 <=1.21.11"
//	  - default: java_21
//
// Order matters: the first matching mapping wins. The "default" mapping is
// used when no other mapping matches. Its value may be either an image key
// (from the images map) or a literal image reference.
func (s *Software) ImageForVersion(version string) (string, error) {
	if s == nil {
		return "", errors.New("software is nil")
	}

	if len(s.DockerImages) == 0 {
		return "", errors.New("software has no docker images configured")
	}

	// If no mappings are defined, fall back:
	// - single image: return it
	// - multiple images: ambiguous, require explicit image
	if len(s.Mappings) == 0 {
		if len(s.DockerImages) == 1 {
			for _, img := range s.DockerImages {
				return img, nil
			}
		}
		return "", errors.New("software has no mappings configured and multiple images; image selection is ambiguous")
	}

	normalizedVersion, err := parseVersion(version)
	if err != nil {
		return "", errors.Wrapf(err, "invalid version %q", version)
	}

	var defaultTarget string

	for _, mapping := range s.Mappings {
		for key, expr := range mapping {
			if key == "default" {
				defaultTarget = expr
				continue
			}

			if expr == "" {
				continue
			}

			if matchesVersionConstraint(normalizedVersion, expr) {
				img, ok := s.DockerImages[key]
				if !ok {
					return "", errors.Errorf("no docker image defined for variant %q", key)
				}
				return img, nil
			}
		}
	}

	// No explicit mapping matched; fall back to default if present
	if defaultTarget != "" {
		if img, ok := s.DockerImages[defaultTarget]; ok {
			return img, nil
		}
		// If default doesn't match an image key, treat it as a literal image
		return defaultTarget, nil
	}

	return "", errors.Errorf("no image mapping found for version %q", version)
}

// parseVersion turns a version string like "1.21.9-pre2" into a slice of
// integers [1, 21, 9]. Any pre-release / build metadata suffix is ignored.
func parseVersion(v string) ([]int, error) {
	v = strings.TrimSpace(v)
	if v == "" {
		return nil, errors.New("empty version")
	}

	// Capture the numeric portion (e.g. "1.21.9" from "1.21.9-pre2")
	re := regexp.MustCompile(`^(\d+(?:\.\d+)*)(?:[^\d].*)?$`)
	matches := re.FindStringSubmatch(v)
	if matches == nil {
		return nil, errors.Errorf("version %q does not start with a numeric segment", v)
	}

	core := matches[1]
	parts := strings.Split(core, ".")
	result := make([]int, len(parts))

	for i, p := range parts {
		n, err := strconv.Atoi(p)
		if err != nil {
			return nil, errors.Wrapf(err, "invalid numeric component %q in version %q", p, v)
		}
		result[i] = n
	}

	return result, nil
}

// matchesVersionConstraint checks whether the given parsed version satisfies
// a constraint expression such as ">=1.17 <=1.17.1" or "<=1.16.5".
//
// The expression is a space-separated list of comparator tokens:
//   - "<=1.16.5"
//   - ">=1.17"
//   - "==1.18" or "1.18" (equality)
//
// All tokens must match (logical AND).
func matchesVersionConstraint(version []int, expr string) bool {
	expr = strings.TrimSpace(expr)
	if expr == "" {
		return false
	}

	tokens := strings.Fields(expr)
	for _, tok := range tokens {
		op, refStr := splitComparatorToken(tok)
		refVer, err := parseVersion(refStr)
		if err != nil {
			return false
		}

		cmp := compareVersions(version, refVer)
		switch op {
		case ">":
			if !(cmp > 0) {
				return false
			}
		case ">=":
			if !(cmp >= 0) {
				return false
			}
		case "<":
			if !(cmp < 0) {
				return false
			}
		case "<=":
			if !(cmp <= 0) {
				return false
			}
		case "==", "=":
			if cmp != 0 {
				return false
			}
		case "":
			// No operator means equality
			if cmp != 0 {
				return false
			}
		default:
			// Unknown operator
			return false
		}
	}

	return true
}

// splitComparatorToken splits a token like ">=1.17" into (">=", "1.17").
// If no operator is present, the operator is "" and the entire token is the version.
func splitComparatorToken(tok string) (string, string) {
	tok = strings.TrimSpace(tok)
	if tok == "" {
		return "", ""
	}

	// Check multi-char operators first
	for _, op := range []string{">=", "<=", "=="} {
		if strings.HasPrefix(tok, op) {
			return op, strings.TrimSpace(tok[len(op):])
		}
	}

	// Single-char operators
	for _, op := range []string{">", "<", "="} {
		if strings.HasPrefix(tok, op) {
			return op, strings.TrimSpace(tok[len(op):])
		}
	}

	return "", tok
}

// compareVersions compares two parsed versions.
// Returns -1 if a < b, 0 if a == b, 1 if a > b.
func compareVersions(a, b []int) int {
	maxLen := len(a)
	if len(b) > maxLen {
		maxLen = len(b)
	}

	for i := 0; i < maxLen; i++ {
		var av, bv int
		if i < len(a) {
			av = a[i]
		}
		if i < len(b) {
			bv = b[i]
		}

		if av < bv {
			return -1
		}
		if av > bv {
			return 1
		}
	}

	return 0
}

