package balancer

import "sync"

// Simple round-robin load balancer
// For development testing

type RoundRobinBalancer struct {
	mu    sync.Mutex
	nodes []BalancedNode
	index int
}

func NewRoundRobin() *RoundRobinBalancer {
	return &RoundRobinBalancer{
		nodes: make([]BalancedNode, 0),
	}
}

func (b *RoundRobinBalancer) PickNode() BalancedNode {
	b.mu.Lock()
	defer b.mu.Unlock()

	if len(b.nodes) == 0 {
		return nil
	}

	n := b.nodes[b.index]
	b.index = (b.index + 1) % len(b.nodes)
	return n
}

func (b *RoundRobinBalancer) AddNode(n BalancedNode) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.nodes = append(b.nodes, n)
}

func (b *RoundRobinBalancer) RemoveNode(target BalancedNode) {
	b.mu.Lock()
	defer b.mu.Unlock()

	for i, n := range b.nodes {
		if n.Id() == target.Id() { // compare by Id instead of pointer
			b.nodes = append(b.nodes[:i], b.nodes[i+1:]...)
			if b.index >= len(b.nodes) && len(b.nodes) > 0 {
				b.index = 0
			}
			break
		}
	}
}
