package models

import (
	"bytes"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"

	"github.com/apex/log"
	"protoxon.com/sls/daemon/parser"
)

// ProcessConfiguration defines the process configuration for a given server
// instance. This sets what SLS is looking for to mark a server as done
// starting what to do when stopping, and what changes to make to the
// configuration file for a server.
type ProcessConfiguration struct {
	Startup struct {
		Done      []*OutputLineMatcher `json:"done"`
		StripAnsi bool                 `json:"strip_ansi"`
	} `json:"startup"`
	Stop               ProcessStopConfiguration   `json:"stop"`
	ConfigurationFiles []parser.ConfigurationFile `json:"configs"`
}

// ProcessStopConfiguration defines what is used when stopping an instance.
type ProcessStopConfiguration struct {
	Type  string `json:"type"`
	Value string `json:"value"`
}

type OutputLineMatcher struct {
	// raw string to match against. This may or may not be prefixed with
	// `regex:` which indicates we want to match against the regex expression.
	raw []byte
	reg *regexp.Regexp
}

// Matches determines if the provided byte string matches the given regex or
// raw string provided to the matcher.
func (olm *OutputLineMatcher) Matches(s []byte) bool {
	if olm.reg == nil {
		return bytes.Contains(s, olm.raw)
	}
	return olm.reg.Match(s)
}

func NewOutputLineMatcher(done string) (*OutputLineMatcher, error) {
	m := &OutputLineMatcher{}

	if strings.HasPrefix(done, "regex:") {
		pattern := strings.TrimPrefix(done, "regex:")
		reg, err := regexp.Compile(pattern)
		if err != nil {
			return nil, fmt.Errorf("invalid regex in done: %w", err)
		}
		m.reg = reg
	} else {
		m.raw = []byte(done)
	}

	return m, nil
}

// String returns the matcher's raw comparison string.
func (olm *OutputLineMatcher) String() string {
	return string(olm.raw)
}

// UnmarshalJSON unmarshals the startup lines into individual structs for easier
// matching abilities.
func (olm *OutputLineMatcher) UnmarshalJSON(data []byte) error {
	var r string
	if err := json.Unmarshal(data, &r); err != nil {
		return err
	}

	olm.raw = []byte(r)
	if bytes.HasPrefix(olm.raw, []byte("regex:")) && len(olm.raw) > 6 {
		r, err := regexp.Compile(strings.TrimPrefix(string(olm.raw), "regex:"))
		if err != nil {
			log.WithField("error", err).WithField("raw", string(olm.raw)).Warn("failed to compile output line marked as being regex")
		}
		olm.reg = r
	}

	return nil
}
