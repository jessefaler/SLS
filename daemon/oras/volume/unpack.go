package volume

import (
	"context"

	ocispec "github.com/opencontainers/image-spec/specs-go/v1"
	"oras.land/oras-go/v2/content"
)

// Unpack unpacks an oci artifact into the destination
func Unpack(ctx context.Context, src content.Fetcher, desc ocispec.Descriptor, dest string) error {
	return nil
	// todo implement this
}
