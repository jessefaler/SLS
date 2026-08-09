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

// Register adds a mixin to the registry.
func (registry *MixinRegistry) Register(mixin *Mixin) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	registry.mixins[mixin.Meta.ID] = mixin
}

// RegisterAll adds a slice of mixins to the registry.
func (registry *MixinRegistry) RegisterAll(mixins []*Mixin) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()

	for _, mixin := range mixins {
		registry.mixins[mixin.Meta.ID] = mixin
	}
}

// Clear removes all mixins from the registry.
func (registry *MixinRegistry) Clear() {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	registry.mixins = make(map[string]*Mixin)
}

// ReplaceAll atomically replaces the entire registry with the provided mixin list.
func (registry *MixinRegistry) ReplaceAll(mixins []*Mixin) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	newMap := make(map[string]*Mixin, len(mixins))
	for _, m := range mixins {
		newMap[m.Meta.ID] = m
	}
	registry.mixins = newMap
}

// Get returns the mixin with the given ID, or nil if not found.
func (registry *MixinRegistry) Get(id string) *Mixin {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()
	return registry.mixins[id]
}

// Find returns all elements from the collection matching the filter.
// If none are found it returns an empty slice.
func (registry *MixinRegistry) Find(filter func(match *Mixin) bool) *[]Mixin {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()
	mixins := make([]Mixin, 0)
	for _, mixin := range registry.mixins {
		if filter(mixin) {
			mixins = append(mixins, *mixin)
		}
	}
	return &mixins
}

// All returns a slice containing all mixins.
func (registry *MixinRegistry) All() []*Mixin {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()
	mixins := make([]*Mixin, len(registry.mixins))
	i := 0
	for _, mixin := range registry.mixins {
		mixins[i] = mixin
		i++
	}
	return mixins
}

// AllMeta returns a slice containing the Meta field of all registered mixins.
func (registry *MixinRegistry) AllMeta() []MixinMeta {
	registry.mutex.RLock()
	defer registry.mutex.RUnlock()

	metas := make([]MixinMeta, len(registry.mixins))
	i := 0
	for _, mixin := range registry.mixins {
		metas[i] = mixin.Meta
		i++
	}
	return metas
}
