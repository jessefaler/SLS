package docker

import (
	"bufio"
	"context"
	"strings"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/buger/jsonparser"
	"github.com/containerd/platforms"
	"github.com/docker/docker/api/types/image"
	"github.com/docker/docker/client"
	"protoxon.com/sls/daemon/config"
)

// registryAuthB64ForImage returns the X-Registry-Auth header for pull operations.
func registryAuthB64ForImage(img string) string {
	var registryAuth *config.RegistryConfiguration
	for registry, c := range config.Get().Docker.Registries {
		if !strings.HasPrefix(img, registry) {
			continue
		}
		log.WithField("registry", registry).Debug("using authentication for registry")
		registryAuth = &c
		break
	}
	if registryAuth == nil {
		return ""
	}
	b64, err := registryAuth.Base64()
	if err != nil {
		log.WithError(err).Error("failed to get registry auth credentials")
		return ""
	}
	return b64
}

func hostPlatformPullString() string {
	return platforms.Format(platforms.DefaultSpec())
}

// PullImage streams an ImagePull until completion. onStatus receives combined status+progress lines when non-nil.
func PullImage(ctx context.Context, cli *client.Client, img string, onStatus func(status string)) error {
	opts := image.PullOptions{
		All:          false,
		Platform:     hostPlatformPullString(),
		RegistryAuth: registryAuthB64ForImage(img),
	}
	out, err := cli.ImagePull(ctx, img, opts)
	if err != nil {
		return err
	}
	defer out.Close()

	log.WithField("image", img).Debug("pulling docker image... this could take a bit of time")

	scanner := bufio.NewScanner(out)
	for scanner.Scan() {
		if onStatus == nil {
			continue
		}
		b := scanner.Bytes()
		status, _ := jsonparser.GetString(b, "status")
		progress, _ := jsonparser.GetString(b, "progress")
		onStatus(strings.TrimSpace(status + " " + progress))
	}
	return scanner.Err()
}

// PullImageWithOfflineFallback pulls img; if the pull fails (e.g. network), it continues when the same tag exists locally.
func PullImageWithOfflineFallback(ctx context.Context, cli *client.Client, img string, onStatus func(status string)) error {
	err := PullImage(ctx, cli, img, onStatus)
	if err == nil {
		log.WithField("image", img).Debug("completed docker image pull")
		return nil
	}

	images, ierr := cli.ImageList(ctx, image.ListOptions{})
	if ierr != nil {
		return errors.Wrap(ierr, "environment/docker: failed to list images")
	}

	for _, im := range images {
		for _, t := range im.RepoTags {
			if t != img {
				continue
			}
			log.WithFields(log.Fields{
				"image": img,
				"err":   err.Error(),
			}).Warn("unable to pull requested image from remote source, however the image exists locally")
			return nil
		}
	}

	return errors.Wrapf(err, "environment/docker: failed to pull %q", img)
}

// pullTimeout is the maximum time allowed for a single image pull.
const pullTimeout = 15 * time.Minute
