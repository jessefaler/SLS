package docker

import (
	"context"
	"strings"
	"sync"

	"github.com/apex/log"
	"github.com/robfig/cron/v3"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/environment"
)

var scheduledPullImages sync.Map // map[string]struct{}

// RegisterScheduledPullImage records an image ref for periodic pulls when image_pull_policy is Schedule.
func RegisterScheduledPullImage(img string) {
	if img == "" || strings.HasPrefix(img, "~") {
		return
	}
	scheduledPullImages.Store(img, struct{}{})
}

// StartScheduledImagePulls runs a cron that pulls registered images when policy is Schedule.
func StartScheduledImagePulls(ctx context.Context) {
	cfg := config.Get().Docker
	if cfg.ImagePullPolicy != config.ImagePullPolicySchedule {
		return
	}

	c := cron.New()
	_, err := c.AddFunc(cfg.ImagePullSchedule, func() {
		runScheduledPulls()
	})
	if err != nil {
		log.WithError(err).Error("environment/docker: invalid scheduled image pull cron")
		return
	}
	c.Start()
	log.WithField("schedule", cfg.ImagePullSchedule).Info("scheduled docker image pulls enabled")

	go func() {
		<-ctx.Done()
		stopCtx := c.Stop()
		<-stopCtx.Done()
	}()
}

func runScheduledPulls() {
	cli, err := environment.Docker()
	if err != nil {
		log.WithError(err).Warn("environment/docker: scheduled image pull skipped (no docker client)")
		return
	}

	// This will pull images one at a time that way we don't overwhelm the network
	scheduledPullImages.Range(func(key, _ any) bool {
		img, ok := key.(string)
		if !ok || img == "" {
			return true
		}
		log.WithField("image", img).Info("environment/docker: scheduled image pull")
		ctx, cancel := context.WithTimeout(context.Background(), pullTimeout)
		err := PullImageWithOfflineFallback(ctx, cli, img, nil)
		cancel()
		if err != nil {
			log.WithError(err).WithField("image", img).Warn("environment/docker: scheduled image pull failed")
		}
		return true
	})
}
