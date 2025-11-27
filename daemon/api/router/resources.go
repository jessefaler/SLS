package router

import (
	"protoxon.com/sls/daemon/server"
)

// Resources holds shared dependencies for an API server.
type Resources struct {
	ServerManager *server.Manager
	VerifyToken   func(token string) bool
}
