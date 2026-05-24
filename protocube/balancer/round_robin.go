package balancer

import "sync"

// Simple round-robin load balancer
// For development testing

type RoundRobinBalancer struct {
	mu      sync.Mutex
	nodes   []BalancedNode
	nodeMap map[string]int // id -> index
	index   int
}

func NewRoundRobin() *RoundRobinBalancer {
	return &RoundRobinBalancer{
		nodes:   make([]BalancedNode, 0),
		nodeMap: make(map[string]int),
	}
}

func (b *RoundRobinBalancer) PickNode() BalancedNode {
	b.mu.Lock()
	defer b.mu.Unlock()
	if len(b.nodes) == 0 {
		return nil
	}

	for i := 0; i < len(b.nodes); i++ {
		n := b.nodes[b.index]
		b.index = (b.index + 1) % len(b.nodes)
		if !n.Drained() {
			return n
		}
	}

	return nil // all nodes are drained
}

func (b *RoundRobinBalancer) AddNode(n BalancedNode) {
	b.mu.Lock()
	defer b.mu.Unlock()

	id := n.Id()

	if idx, exists := b.nodeMap[id]; exists {
		b.nodes[idx] = n
		return
	}

	b.nodes = append(b.nodes, n)
	b.nodeMap[id] = len(b.nodes) - 1
}

func (b *RoundRobinBalancer) RemoveNode(target BalancedNode) {
	b.mu.Lock()
	defer b.mu.Unlock()

	id := target.Id()

	idx, exists := b.nodeMap[id]
	if !exists {
		return
	}

	lastIdx := len(b.nodes) - 1
	lastNode := b.nodes[lastIdx]

	// Move last node into removed spot (if not same)
	b.nodes[idx] = lastNode
	b.nodeMap[lastNode.Id()] = idx

	// Shrink slice
	b.nodes = b.nodes[:lastIdx]
	delete(b.nodeMap, id)

	// Fix round-robin index
	if b.index >= len(b.nodes) && len(b.nodes) > 0 {
		b.index = 0
	}
}

func (b *RoundRobinBalancer) ListNodes() []BalancedNode {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.nodes
}
