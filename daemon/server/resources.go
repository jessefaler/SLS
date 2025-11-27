package server

import (
	"sync"

	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/system"
)

// ResourceUsage defines the current resource usage for a given server instance. If a server is offline you
// should obviously expect memory and CPU usage to be 0. However, disk will always be returned
// since that is not dependent on the server being running to collect that data.
type ResourceUsage struct {
	mu sync.RWMutex

	// Embed the current environment stats into this server specific resource usage struct.
	environment.Stats

	// The current server status.
	State *system.AtomicString `json:"state"`
}

// Proc returns the current resource usage stats for the server instance. This returns
// a copy of the tracked resources.
func (s *Server) Proc() ResourceUsage {
	s.resources.mu.Lock()
	defer s.resources.mu.Unlock()
	//goland:noinspection GoVetCopyLock
	return s.resources
}

// UpdateStats updates the current stats for the server's resource usage.
func (ru *ResourceUsage) UpdateStats(stats environment.Stats) {
	ru.mu.Lock()
	ru.Stats = stats
	ru.mu.Unlock()
}

// Reset resets the usages values to zero, used when a server is stopped to ensure we don't hold
// onto any values incorrectly.
func (ru *ResourceUsage) Reset() {
	ru.mu.Lock()
	defer ru.mu.Unlock()

	ru.Memory = 0
	ru.CpuAbsolute = 0
	ru.Uptime = 0
	ru.Network.TxBytes = 0
	ru.Network.RxBytes = 0
}
