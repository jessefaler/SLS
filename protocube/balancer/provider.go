package balancer

import (
	"sync"

	"emperror.dev/errors"
)

type Provider struct {
	mu sync.RWMutex
	lb Balancer
}

func NewProvider(initial Balancer) *Provider {
	return &Provider{lb: initial}
}

func (p *Provider) Get() Balancer {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.lb
}

// Set Sets the load balancer for the system to use.
// Nodes that have already registered will not be automatically added
// if you are setting this after nodes have registered you should call
// the SetWithNodes method and include the currently registered nodes
func (p *Provider) Set(lb Balancer) error {
	if lb == nil {
		return errors.New("balancer cannot be nil")
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	p.lb = lb
	return nil
}

// SetWithNodes replaces the current balancer with a new one
// and populates it with the provided nodes.
func (p *Provider) SetWithNodes(lb Balancer, nodes []BalancedNode) error {
	if lb == nil {
		return errors.New("balancer cannot be nil")
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	// Add all nodes to the new balancer
	for _, n := range nodes {
		lb.AddNode(n)
	}

	// Swap in the new balancer
	p.lb = lb
	return nil
}
