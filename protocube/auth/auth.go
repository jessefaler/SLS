package auth

import (
	"github.com/grokify/coreforge/identity/apikey"
	"protoxon.com/sls/protocube/auth/scope"
)

type KeyService struct {
	*apikey.Service
}

// Configures and creates a new api key service
func NewKeyService() *KeyService {
	config := apikey.ServiceConfig{
		Store:         NewCachedStore(CachedKeyTTL),
		Prefix:        "sls",
		AllowedScopes: []string{scope.AppAdmin, scope.Node},
	}
	svc := apikey.NewService(config)
	return &KeyService{
		svc,
	}
}
