package router

import (
	"protoxon.com/sls/protocube/auth"
	"protoxon.com/sls/protocube/balancer"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/node"
	"protoxon.com/sls/protocube/server"
	"protoxon.com/sls/protocube/software"
)

// Resources holds shared dependencies for an API server.
type Resources struct {
	ServerManager     *server.Manager
	BlueprintRegistry *blueprint.Registry
	SoftwareRegistry  *software.Registry
	NodeManager       *node.Manager
	LoadBalancer      *balancer.Provider
	Client            *client.Client
	VerifyToken       func(token string, keyType auth.KeyType) (bool, error)
}
