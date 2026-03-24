package scope

// Note: Adding a new scope here requires you to add it to the allowed scope in key.go for it to be allowed in the auth service

const (
	// Application api key scope
	AppAdmin = "app:admin"
	// Node api scope
	Node = "node"
)
