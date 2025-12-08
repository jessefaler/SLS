package blueprint

import (
	"sync"
)

type Registry struct {
	mutex      sync.RWMutex
	blueprints map[string]*Blueprint
}

// NewRegistry returns a new blueprint registry instance.
func NewRegistry() *Registry {
	return &Registry{
		blueprints: make(map[string]*Blueprint),
	}
}

// Register Adds a blueprint to the registry
func (registry *Registry) Register(blueprint *Blueprint) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	registry.blueprints[blueprint.Meta.ID] = blueprint
}

// RegisterAll adds a slice of blueprints to the registry
func (registry *Registry) RegisterAll(blueprints []*Blueprint) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()

	for _, blueprint := range blueprints {
		registry.blueprints[blueprint.Meta.ID] = blueprint
	}
}

// Clear removes all blueprints from the registry.
func (registry *Registry) Clear() {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	registry.blueprints = make(map[string]*Blueprint)
}

// ReplaceAll atomically replaces the entire registry with the provided blueprint list.
func (registry *Registry) ReplaceAll(blueprints []*Blueprint) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	// Replace the entire map in a single lock window
	newMap := make(map[string]*Blueprint, len(blueprints))
	for _, bp := range blueprints {
		newMap[bp.Meta.ID] = bp
	}
	registry.blueprints = newMap
}

// Get returns the blueprint with the given ID, or nil if not found.
func (registry *Registry) Get(id string) *Blueprint {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()
	return registry.blueprints[id]
}

// Find returns all elements from the collection matching the filter.
// If none are found it returns an empty slice
func (registry *Registry) Find(filter func(match *Blueprint) bool) *[]Blueprint {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()
	var blueprints []Blueprint
	for _, blueprint := range registry.blueprints {
		if filter(blueprint) {
			blueprints = append(blueprints, *blueprint)
		}
	}
	return &blueprints
}

// FindType return a slice containing all blueprints matching the provided type
func (registry *Registry) FindType(blueprintType string) *[]Blueprint {
	return registry.Find(func(match *Blueprint) bool {
		return match.Meta.Type == blueprintType
	})
}

// All return a slice containing all blueprints
func (registry *Registry) All() []*Blueprint {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()
	var blueprints []*Blueprint
	for _, blueprint := range registry.blueprints {
		blueprints = append(blueprints, blueprint)
	}
	return blueprints
}

// AllMeta returns a slice containing the Meta field of all registered blueprints.
func (registry *Registry) AllMeta() []Meta {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()

	metas := make([]Meta, 0, len(registry.blueprints))
	for _, blueprint := range registry.blueprints {
		metas = append(metas, blueprint.Meta)
	}
	return metas
}
