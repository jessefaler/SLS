package balancer

// BalancedNode represents a node that can be managed by a Balancer.
type BalancedNode interface {
	Id() string
	Name() string
	Url() string
	Drained() bool
}

// Balancer defines the interface for selecting nodes for server creation.
type Balancer interface {
	// PickNode returns a node suitable for running a new server.
	// Returns nil if no healthy nodes are available.
	PickNode() BalancedNode

	// AddNode registers a node in the balancer.
	AddNode(n BalancedNode)

	// RemoveNode removes a node from the balancer
	RemoveNode(n BalancedNode)

	// ListNodes returns a slice of all BalancedNode's in balancer
	ListNodes() []BalancedNode
}
