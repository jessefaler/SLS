package server

import (
	"path/filepath"

	"emperror.dev/errors"
	"github.com/apex/log"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/environment"
	"protoxon.com/sls/protocube/models"
	"protoxon.com/sls/protocube/software"
)

// BuildServerConfiguration constructs a servers runtime configuration and installation script
func BuildServerConfiguration(s *Server, bp *blueprint.Blueprint, swr *software.Registry, alloc environment.Allocations) (*models.ServerConfiguration, *software.InstallationScript, error) {
	effectiveSoftware := bp.Server.Software
	effectiveVersion := bp.Server.Version
	if s.Overrides != nil {
		if s.Overrides.Software != nil {
			effectiveSoftware = *s.Overrides.Software
		}
		if s.Overrides.Version != nil {
			effectiveVersion = *s.Overrides.Version
		}
	}

	sw := swr.Get(effectiveSoftware)
	if sw == nil {
		return nil, nil, errors.Errorf("software not found: %s", effectiveSoftware)
	}

	matcher, err := models.NewOutputLineMatcher(sw.OnlineSignal)
	if err != nil {
		return nil, nil, errors.Wrapf(err, "failed to create output line matcher for the start configuration: %s", sw.OnlineSignal)
	}

	// Convert software, blueprint, and optional request override patches
	// to config file patches (overrides merge/override when applied last).
	var overrideConfigs map[string]blueprint.ConfigFile
	if s.Overrides != nil && s.Overrides.Configs != nil {
		overrideConfigs = s.Overrides.Configs
	}
	configFiles, cfgErr := GetConfigFiles(sw, bp, overrideConfigs)

	// log any errors that occurred when converting configuration patches
	if cfgErr != nil {
		log.WithError(cfgErr).Warn("an error occurred while converting config patches for server " + s.Id())
	}

	pc := &models.ProcessConfiguration{
		Startup: struct {
			Done      []*models.OutputLineMatcher `json:"done"`
			StripAnsi bool                        `json:"strip_ansi"`
		}{
			Done:      []*models.OutputLineMatcher{matcher},
			StripAnsi: false,
		},
		Stop: models.ProcessStopConfiguration{
			Type:  "command",
			Value: sw.StopCommand,
		},
		ConfigurationFiles: configFiles,
	}

	// Handle Overrides
	save := bp.Save
	// We need to make a copy of the limits so we don't mutate the blueprints default limits
	limits := environment.CopyLimits(bp.Server.Limits)
	if s.Overrides != nil {
		if s.Overrides.Save != nil {
			save = *s.Overrides.Save
		}
		limits = environment.MergeLimits(limits, s.Overrides.Limits)
	}

	serverFolder := bp.Server.Path
	if s.Overrides != nil && (s.Overrides.Software != nil || s.Overrides.Version != nil) {
		serverFolder = filepath.Join(effectiveSoftware, effectiveVersion)
	}

	image := bp.Server.Image
	if s.Overrides != nil && s.Overrides.Image != nil {
		image = *s.Overrides.Image
	}
	// If no explicit image is set on the blueprint or via overrides,
	// fall back to the software's image mappings selection.
	if image == "" {
		selectedImage, err := sw.ImageForVersion(effectiveVersion)
		if err != nil {
			return nil, nil, errors.Wrapf(err, "failed to select image for version %s", effectiveVersion)
		}
		image = selectedImage
	}

	var env map[string]string
	if s.Overrides != nil {
		env = s.Overrides.Env
	}
	effectiveState := blueprint.MergeState(bp.State, env)

	cfg := models.ServerConfiguration{
		Id:                   s.Id(),
		ProcessConfiguration: pc,
		Image:                image,
		Invocation:           sw.Invocation,
		Limits:               limits,
		State:                effectiveState,
		ServerFolder:         serverFolder,
		Allocations:          alloc,
		Save:                 save,
		SoftwareId:           sw.Id,
		SoftwareVersion:      effectiveVersion,
		HasInstallScript:     sw.InstallScript.Script != "",
		SkipInstallScript:    sw.InstallScript.SkipScripts,
	}

	installScript := sw.InstallScript
	return &cfg, &installScript, nil
}
