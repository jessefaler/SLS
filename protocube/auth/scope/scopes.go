package scope

// Note: Adding a new scope here requires you to add it to the allowed scopes in auth.go for it to be allowed in the key service

const (
	// Application api key scopes
	AppAdmin = "app:admin"
	// Node api scopes
	Node = "node"
)
