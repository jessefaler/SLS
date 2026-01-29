package allocator

import (
	"math/rand"
	"strconv"
	"strings"

	"emperror.dev/errors"
	"protoxon.com/sls/daemon/config"
)

type Allocator struct {
	allocations map[string]map[string]struct{} // ip -> set of ports
	next        int                            // the next interface to make an allocation on
}

func (a *Allocator) Allocate() bool {
	allocation := config.Get().Allocations[a.next]
	allocation.Ports
}

func (a *Allocator) AllocatePort(allocation config.Allocation) (int, error) {
	// Parse the range string, e.g. "49152-65535"
	parts := strings.Split(allocation.Ports, "-")
	if len(parts) != 2 {
		return -1, errors.New("Invalid port range in daemon config")
	}

	minPort, err := strconv.Atoi(parts[0])
	if err != nil {
		return -1, errors.Wrap(err, "Failed to parse port range")
	}

	maxPort, err := strconv.Atoi(parts[1])
	if err != nil {
		return -1, errors.Wrap(err, "Failed to parse port range")
	}

	// Pick a random port in range
	port := rand.Intn(maxPort-minPort+1) + minPort
	a.allocations[allocation.Address]

	return port, nil
}

func (a *Allocator) allocate(ip, port string) bool {
	if a.allocations[ip] == nil {
		a.allocations[ip] = make(map[string]struct{})
	}

	if _, exists := a.allocations[ip][port]; exists {
		return false // already allocated
	}

	a.allocations[ip][port] = struct{}{}
	return true
}

func (a *Allocator) Free(ip, port string) {
	if ports, ok := a.allocations[ip]; ok {
		delete(ports, port)
		if len(ports) == 0 {
			delete(a.allocations, ip)
		}
	}
}
