package config

import (
	"fmt"
	"strings"

	"github.com/robfig/cron/v3"
)

// DefaultImagePullSchedule is used when image_pull_policy is Schedule and image_pull_schedule is omitted or empty.
// Runs at 01:00 on the 1st of every month.
const DefaultImagePullSchedule = "0 1 1 * *"

// ImagePullPolicy controls when the daemon pulls a container image
type ImagePullPolicy string

const (
	ImagePullPolicyAlways       ImagePullPolicy = "Always"
	ImagePullPolicyIfNotPresent ImagePullPolicy = "IfNotPresent"
	ImagePullPolicyNever        ImagePullPolicy = "Never"
	ImagePullPolicySchedule     ImagePullPolicy = "Schedule"
)

// ParseImagePullPolicy normalizes and validates a policy string
// An empty value defaults to Schedule (see DefaultImagePullSchedule for the cron default).
func ParseImagePullPolicy(s string) (ImagePullPolicy, error) {
	switch strings.TrimSpace(strings.ToLower(s)) {
	case "":
		return ImagePullPolicySchedule, nil
	case "ifnotpresent":
		return ImagePullPolicyIfNotPresent, nil
	case "always":
		return ImagePullPolicyAlways, nil
	case "never":
		return ImagePullPolicyNever, nil
	case "schedule":
		return ImagePullPolicySchedule, nil
	default:
		return "", fmt.Errorf("invalid docker.image_pull_policy %q: want Always, IfNotPresent, Never, or Schedule", s)
	}
}

// ValidateImagePullPolicy applies defaults and checks docker.image_pull_schedule when needed.
func ValidateImagePullPolicy(cfg *DockerConfiguration) error {
	p, err := ParseImagePullPolicy(string(cfg.ImagePullPolicy))
	if err != nil {
		return err
	}
	cfg.ImagePullPolicy = p

	if cfg.ImagePullPolicy != ImagePullPolicySchedule {
		return nil
	}

	schedule := strings.TrimSpace(cfg.ImagePullSchedule)
	if schedule == "" {
		schedule = DefaultImagePullSchedule
	}
	if _, err := cron.ParseStandard(schedule); err != nil {
		return fmt.Errorf("invalid docker.image_pull_schedule cron expression: %w", err)
	}
	cfg.ImagePullSchedule = schedule
	return nil
}
