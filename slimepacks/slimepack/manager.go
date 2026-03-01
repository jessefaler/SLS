package slimepack

import (
	"context"
	"path/filepath"
	"sync"
	"time"

	"protoxon.com/sls/slimepacks/config"
	"protoxon.com/sls/slimepacks/log"
	"protoxon.com/sls/slimepacks/mappings"

	"emperror.dev/errors"
	"protoxon.com/sls/protocube/blueprint"
)

var Snapshots *mappings.Snapshots
var Mutex sync.RWMutex

type Manager struct {
	Packs       map[string]*Pack
	PackFormats *mappings.FormatsTable
}

func NewManager() (*Manager, error) {
	packFormats, err := mappings.LoadPackFormats()
	if err != nil {
		return nil, errors.Wrap(err, "failed to load pack formats")
	}

	Snapshots, err = mappings.LoadSnapshotMappings()
	if err != nil {
		return nil, errors.Wrap(err, "failed to load built in snapshot mappings")
	}

	m := &Manager{
		Packs:       make(map[string]*Pack),
		PackFormats: packFormats,
	}

	// Try to load snapshot mappings from mojang's version manifest
	// That way we don't have to keep snapshots.json upto date constantly
	// If it fails fall back to snapshot.json
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 1*time.Minute)
		defer cancel()
		snapshots, err := mappings.FetchSnapshotMappings(ctx)
		if err != nil {
			log.Warn("Failed to fetch snapshot mappings from mojang version manifest falling back to built in mappings")
		}
		Mutex.Lock()
		Snapshots = snapshots
		defer Mutex.Unlock()
	}()

	return m, nil
}

func (manager *Manager) GetPack(name string) *Pack {
	return manager.Packs[name]
}

func (manager *Manager) GetPacks() []*Pack {
	packs := make([]*Pack, 0, len(manager.Packs))
	for _, pack := range manager.Packs {
		packs = append(packs, pack)
	}
	return packs
}

func (manager *Manager) AddPack(name string, pack *Pack) {
	manager.Packs[name] = pack
}

func (manager *Manager) RemovePack(name string) {
	delete(manager.Packs, name)
}

// LoadAllPacks scans all blueprints for a "resource_pack" annotation.
// For each blueprint that has one, a pack is created and registered.
func (manager *Manager) LoadAllPacks(bpr *blueprint.Registry) {
	root := config.Get().ResourcePacksRoot
	for _, bp := range bpr.All() {
		if pack, ok := bp.Annotations["resource_pack"].(string); ok {
			pack := NewPack(bp.Meta.ID, filepath.Join(root, pack), manager.PackFormats)
			manager.AddPack(bp.Meta.ID, pack)
		}
	}
}
