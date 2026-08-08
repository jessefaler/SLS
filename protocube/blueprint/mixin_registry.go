package blueprint

import "sync"

// Mixins are stored with their inheritance chains resolved.
// This avoids resolving the same inheritance chain each time a mixin is referenced.

type MixinRegistry struct {
	mutex  sync.RWMutex
	mixins map[string]*Mixin
}

// NewMixinRegistry returns a new mixin registry instance.
func NewMixinRegistry() *MixinRegistry {
	return &MixinRegistry{
		mixins: make(map[string]*Mixin),
	}
}
