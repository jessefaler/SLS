package client

import (
	"emperror.dev/errors"
	"oras.land/oras-go/v2/registry/remote/credentials"
)

// todo use own credentials and fallback to docker etc if none have the correct credentials prompt

func NewCredentialStore() (credentials.Store, error) {
	opts := credentials.StoreOptions{}
	docker, err := credentials.NewStoreFromDocker(opts)
	if err != nil {
		return nil, errors.Wrap(err, "failed to load docker credentials")
	}

	var fallbacks []credentials.Store
	/*
		for _, path := range podmanAuthFiles() {
			store, err := credentials.NewStore(path, opts)
			if err != nil {
				continue
			}
			fallbacks = append(fallbacks, store)
		}

	*/

	return credentials.NewStoreWithFallbacks(docker, fallbacks...), nil
}
