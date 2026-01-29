package allocator

import (
	"math/rand/v2"
	"strconv"
	"strings"
	"sync"
	"time"

	"emperror.dev/errors"
	"protoxon.com/sls/protocube/enviroment"
	"protoxon.com/sls/protocube/models"
)

var (
	ErrNoAllocations = errors.New("no allocations for address")
	ErrNoFreePorts   = errors.New("no free ports available")
)

// Allocator manages IP and port assignments for servers.
type Allocator struct {
	mu          sync.Mutex
	Used        map[string]map[int]struct{} // ip -> set of ports
	Allocations map[string][]Allocation     // address -> allocations
	rand        *rand.Rand
}

type Allocation struct {
	Address         string
	Alias           string
	ForceOutgoingIP bool
	PortRange       PortRange
}

type PortRange struct {
	Start int
	End   int
}

// NewAllocator constructs an Allocator from router allocations.
func NewAllocator(allocations []models.Allocation) (*Allocator, error) {
	a := &Allocator{
		Used:        make(map[string]map[int]struct{}),
		Allocations: make(map[string][]Allocation),
		rand:        rand.New(rand.NewPCG(uint64(time.Now().UnixNano()), 0)),
	}

	for _, alloc := range allocations {
		portRange, err := getRange(alloc)
		if err != nil {
			return nil, err
		}

		a.Allocations[alloc.Address] = append(a.Allocations[alloc.Address], Allocation{
			Address:         alloc.Address,
			Alias:           alloc.Alias,
			ForceOutgoingIP: alloc.ForceOutgoingIP,
			PortRange:       portRange,
		})
	}

	return a, nil
}

// Release frees a previously allocated port for an address.
func (a *Allocator) Release(address string, port int) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if used, ok := a.Used[address]; ok {
		delete(used, port)
	}
}

// NewAllocation selects a random allocation and returns an Allocations struct.
func (a *Allocator) NewAllocation() (enviroment.Allocations, error) {
	if a == nil {
		return enviroment.Allocations{}, errors.New("allocator is nil")
	}

	a.mu.Lock()
	defer a.mu.Unlock()

	if len(a.Allocations) == 0 {
		return enviroment.Allocations{}, errors.New("no allocations configured")
	}

	// Collect all addresses
	addresses := make([]string, 0, len(a.Allocations))
	for addr := range a.Allocations {
		addresses = append(addresses, addr)
	}

	a.rand.Shuffle(len(addresses), func(i, j int) {
		addresses[i], addresses[j] = addresses[j], addresses[i]
	})

	for _, address := range addresses {
		port, alloc, err := a.allocate(address)
		if err != nil {
			if errors.Is(err, ErrNoFreePorts) {
				continue
			}
			return enviroment.Allocations{}, err
		}

		out := &enviroment.Allocations{
			ForceOutgoingIP: alloc.ForceOutgoingIP,
			Alias:           alloc.Alias,
			Mappings: map[string][]int{
				address: {port},
			},
			Release: func() {
				a.Release(address, port)
			},
		}
		out.DefaultMapping.Ip = address
		out.DefaultMapping.Port = port
		return *out, nil
	}

	return enviroment.Allocations{}, errors.Wrap(ErrNoFreePorts, "no free ports available on any configured allocation")
}

// allocate finds a free port for a given address and returns the allocation used.
func (a *Allocator) allocate(address string) (int, Allocation, error) {
	allocs, ok := a.Allocations[address]
	if !ok || len(allocs) == 0 {
		return 0, Allocation{}, errors.Wrapf(ErrNoAllocations, "address=%s", address)
	}

	// Ensure used map exists
	used, ok := a.Used[address]
	if !ok {
		used = make(map[int]struct{})
		a.Used[address] = used
	}

	for _, alloc := range allocs {
		for port := alloc.PortRange.Start; port <= alloc.PortRange.End; port++ {
			if _, exists := used[port]; exists {
				continue
			}
			used[port] = struct{}{}
			return port, alloc, nil
		}
	}

	return 0, Allocation{}, errors.Wrapf(ErrNoFreePorts, "address=%s", address)
}

// getRange parses a port range string into a PortRange struct.
func getRange(allocation models.Allocation) (PortRange, error) {
	parts := strings.Split(allocation.Ports, "-")
	if len(parts) != 2 {
		return PortRange{}, errors.New("invalid port range in daemon config")
	}

	minPort, err := strconv.Atoi(parts[0])
	if err != nil {
		return PortRange{}, errors.Wrap(err, "failed to parse port range")
	}

	maxPort, err := strconv.Atoi(parts[1])
	if err != nil {
		return PortRange{}, errors.Wrap(err, "failed to parse port range")
	}

	return PortRange{
		Start: minPort,
		End:   maxPort,
	}, nil
}

// Claim marks a specific port on a given address as allocated.
// If the port is not within any configured range, this does nothing.
func (a *Allocator) Claim(address string, port int) {
	a.mu.Lock()
	defer a.mu.Unlock()

	allocs, ok := a.Allocations[address]
	if !ok || len(allocs) == 0 {
		return // no allocations for this address, do nothing
	}

	// Check if port is within any configured range
	inRange := false
	for _, alloc := range allocs {
		if port >= alloc.PortRange.Start && port <= alloc.PortRange.End {
			inRange = true
			break
		}
	}

	if !inRange {
		return // port not in any range, silently skip
	}

	// Ensure used map exists
	if _, ok := a.Used[address]; !ok {
		a.Used[address] = make(map[int]struct{})
	}

	// Mark port as used
	a.Used[address][port] = struct{}{}
}
