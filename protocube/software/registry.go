package software

import (
	"sync"
)

type Registry struct {
	mutex    sync.RWMutex
	software map[string]*Software
}

// NewRegistry returns a new software registry instance.
func NewRegistry() *Registry {
	return &Registry{
		software: make(map[string]*Software),
	}
}

// Register Adds a software configuration to the registry
func (registry *Registry) Register(software *Software) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	registry.software[software.Id] = software
}

// RegisterAll adds a slice of software configurations to the registry
func (registry *Registry) RegisterAll(software []*Software) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()

	for _, s := range software {
		registry.software[s.Id] = s
	}
}

// Clear removes all software configs from the registry.
func (registry *Registry) Clear() {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	registry.software = make(map[string]*Software)
}

// ReplaceAll atomically replaces the entire registry with the provided software list.
func (registry *Registry) ReplaceAll(softwareList []*Software) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()

	newMap := make(map[string]*Software, len(softwareList))
	for _, s := range softwareList {
		newMap[s.Id] = s
	}

	registry.software = newMap
}

// Get returns the software with the given ID, or nil if not found.
func (registry *Registry) Get(id string) *Software {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()
	return registry.software[id]
}

// Find returns all elements from the collection matching the filter.
// If none are found it returns an empty slice
func (registry *Registry) Find(filter func(match *Software) bool) *[]Software {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()
	result := make([]Software, 0)
	for _, software := range registry.software {
		if filter(software) {
			result = append(result, *software)
		}
	}
	return &result
}

// All return a slice containing all software configurations
func (registry *Registry) All() []*Software {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()
	result := make([]*Software, len(registry.software))
	i := 0
	for _, software := range registry.software {
		result[i] = software
		i++
	}
	return result
}
