package client

import (
	"context"

	"emperror.dev/errors"
	ocispec "github.com/opencontainers/image-spec/specs-go/v1"
	"protoxon.com/sls/daemon/oras/volume"
)

// Pull fetches a volume artifact and unpacks its layers into dest.
func Pull(ctx context.Context, reference, dest string) (ocispec.Descriptor, error) {
	repository, err := NewRepository(reference)
	if err != nil {
		return ocispec.Descriptor{}, err
	}

	// Resolve the reference to the manifest descriptor.
	descriptor, err := repository.Resolve(ctx, repository.Reference.Reference)
	if err != nil {
		return ocispec.Descriptor{}, errors.Wrap(err, "resolve volume")
	}

	if err := volume.Unpack(ctx, repository, descriptor, dest); err != nil {
		return ocispec.Descriptor{}, err
	}

	return descriptor, nil
}
