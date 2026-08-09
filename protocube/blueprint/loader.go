package blueprint

import (
	"os"
	"path/filepath"
	"strings"

	"emperror.dev/errors"
	"github.com/apex/log"
	"gopkg.in/yaml.v3"
	"protoxon.com/sls/protocube/software"
)

type LoadResult struct {
	Blueprints []*Blueprint
	Mixins     []*Mixin
}

type documentKind int

const (
	documentUnknown documentKind = iota
	documentBlueprint
	documentMixin
	documentAmbiguous
)

// LoadAll walks root recursively for .yaml/.yml files, classifies each document
// by its top level mixin: or blueprint: section, and loads both kinds.
// Mixins may live anywhere under root; a mixins/ subdirectory is only a convention.
func LoadAll(root string, sw *software.Registry) (*LoadResult, error) {
	softwareRegistry = sw
	if softwareRegistry == nil {
		return nil, errors.New("failed to load blueprints: software registry was nil")
	}

	result := &LoadResult{}
	seenBlueprints := make(map[string]struct{})
	seenMixins := make(map[string]struct{})

	err := filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			log.WithField("path", path).Warnf("blueprint loader: failed to access file: %v", err)
			return nil
		}
		if info.IsDir() {
			return nil
		}

		ext := strings.ToLower(filepath.Ext(info.Name()))
		if ext != ".yaml" && ext != ".yml" {
			return nil
		}

		data, readErr := os.ReadFile(path)
		if readErr != nil {
			log.WithField("path", path).Warnf("blueprint loader: failed to read file: %v", readErr)
			return nil
		}

		kind, classErr := classifyDocument(data)
		if classErr != nil {
			log.WithField("path", path).Warnf("blueprint loader: failed to classify document: %v", classErr)
			return nil
		}

		switch kind {
		case documentMixin:
			m, loadErr := parseMixin(data)
			if loadErr != nil {
				log.WithField("mixin", path).Warnf("Failed to load mixin: %v", loadErr)
				return nil
			}
			if _, exists := seenMixins[m.Meta.ID]; exists {
				log.WithField("id", m.Meta.ID).
					WithField("file", path).
					Errorf("Mixin validation failed: mixin with ID '%s' already exists", m.Meta.ID)
				return nil
			}
			seenMixins[m.Meta.ID] = struct{}{}
			result.Mixins = append(result.Mixins, m)

		case documentBlueprint:
			bp, loadErr := parseBlueprint(data)
			if loadErr != nil {
				log.WithField("blueprint", path).Warnf("Failed to load blueprint: %v", loadErr)
				return nil
			}
			if _, exists := seenBlueprints[bp.Meta.ID]; exists {
				log.WithField("id", bp.Meta.ID).
					WithField("file", path).
					Errorf("Blueprint validation failed: blueprint with ID '%s' already exists", bp.Meta.ID)
				return nil
			}
			seenBlueprints[bp.Meta.ID] = struct{}{}
			result.Blueprints = append(result.Blueprints, bp)

		case documentAmbiguous:
			log.WithField("path", path).
				Error("blueprint loader: document has both mixin: and blueprint: sections; skipping")

		default:
			log.WithField("path", path).
				Warn("blueprint loader: document has neither mixin: nor blueprint: section; skipping")
		}

		return nil
	})
	if err != nil {
		return nil, err
	}

	return Resolve(result), nil
}

// classifyDocument inspects top-level YAML keys to decide document type.
func classifyDocument(data []byte) (documentKind, error) {
	var probe struct {
		Blueprint *yaml.Node `yaml:"blueprint"`
		Mixin     *yaml.Node `yaml:"mixin"`
	}
	if err := yaml.Unmarshal(data, &probe); err != nil {
		return documentUnknown, err
	}

	hasBlueprint := probe.Blueprint != nil
	hasMixin := probe.Mixin != nil
	switch {
	case hasBlueprint && hasMixin:
		return documentAmbiguous, nil
	case hasMixin:
		return documentMixin, nil
	case hasBlueprint:
		return documentBlueprint, nil
	default:
		return documentUnknown, nil
	}
}

func parseBlueprint(data []byte) (*Blueprint, error) {
	var bp Blueprint
	if err := yaml.Unmarshal(data, &bp); err != nil {
		return nil, err
	}
	return &bp, nil
}

func parseMixin(data []byte) (*Mixin, error) {
	var mixin Mixin
	if err := yaml.Unmarshal(data, &mixin); err != nil {
		return nil, err
	}
	return &mixin, nil
}
