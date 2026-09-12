package client

import (
	"net/http"
	"strings"

	"emperror.dev/errors"
	"github.com/google/go-containerregistry/pkg/name"
	"oras.land/oras-go/v2/registry/remote"
	"oras.land/oras-go/v2/registry/remote/auth"
	"oras.land/oras-go/v2/registry/remote/credentials"
	"oras.land/oras-go/v2/registry/remote/retry"
)

const DefaultRegistry = "docker.io"
const DefaultNamespace = "library"

// NewRepository returns an oras repository client
func NewRepository(reference string) (*remote.Repository, error) {
	ref, err := ParseReference(reference)
	if err != nil {
		return nil, errors.Wrap(err, "failed to parse reference")
	}

	repository, err := remote.NewRepository(repositoryName(ref))
	if err != nil {
		return nil, errors.Wrap(err, "failed to create oras repository client")
	}

	repository.Reference.Reference = ref.Identifier()

	store, err := NewCredentialStore()
	if err != nil {
		return nil, err
	}

	repository.Client = &auth.Client{
		Client:     retry.DefaultClient,
		Cache:      auth.DefaultCache,
		Credential: credentials.Credential(store),
		Header:     http.Header{"User-Agent": {"sls"}},
	}

	return repository, nil
}

func ParseReference(artifact string) (name.Reference, error) {
	return name.ParseReference(artifact, name.WithDefaultRegistry(DefaultRegistry), name.WithDefaultTag("latest"))
}

func repositoryName(ref name.Reference) string {
	registry := ref.Context().RegistryStr()
	repo := ref.Context().RepositoryStr()
	if !strings.Contains(repo, "/") {
		repo = DefaultNamespace + "/" + repo
	}
	return registry + "/" + repo
}
