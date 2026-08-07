package mixin

import "sync"

// Mixins are stored with their inheritance chains resolved
// This avoids resolving the same inheritance chain each time a mixin is referenced

type Registry struct {
	mutex  sync.RWMutex
	mixins map[string]*Mixin
}

// NewRegistry returns a new mixin registry instance.
func NewRegistry() *Registry {
	return &Registry{
		mixins: make(map[string]*Mixin),
	}
}
