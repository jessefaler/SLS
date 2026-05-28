package server

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"emperror.dev/errors"
	"github.com/containerd/errdefs"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/mount"
	"github.com/docker/docker/client"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/remote"
	"protoxon.com/sls/daemon/system"
)

const (
	warmupPolicyFail     = "fail"
	warmupPolicyContinue = "continue"
	warmupPolicyRetry    = "retry"
)

func (ip *InstallationProcess) RunWarmup(ctx context.Context) error {
	ip.applyWarmupDefaults()
	if !ip.Script.Warmup {
		return nil
	}

	attempts := 1
	if ip.Script.WarmupFailurePolicy == warmupPolicyRetry {
		attempts += ip.Script.WarmupRetries
	}

	var lastErr error
	for attempt := 1; attempt <= attempts; attempt++ {
		if attempt > 1 {
			ip.Server.Log().WithField("attempt", attempt).Info("retrying server warmup")
		}
		if err := ip.runWarmupAttempt(ctx, attempt); err != nil {
			lastErr = err
			ip.Server.Log().WithField("attempt", attempt).WithField("error", err).Warn("server warmup failed")
			continue
		}
		lastErr = nil
		break
	}

	if lastErr != nil {
		if ip.Script.WarmupFailurePolicy == warmupPolicyContinue {
			ip.Server.FinishInstallPhase(InstallPhaseWarmupFailed, lastErr.Error())
			ip.Server.Log().WithField("error", lastErr).Warn("continuing after warmup failure due to configured policy")
		} else {
			ip.Server.FinishInstallPhase(InstallPhaseWarmupFailed, lastErr.Error())
			return lastErr
		}
	}

	if strings.TrimSpace(ip.Script.PostWarmupScript) == "" {
		return nil
	}
	if err := ip.runPostWarmup(ctx); err != nil {
		ip.Server.FinishInstallPhase(InstallPhasePostWarmupFailed, err.Error())
		if ip.Script.WarmupFailurePolicy == warmupPolicyContinue {
			ip.Server.Log().WithField("error", err).Warn("continuing after post-warmup failure due to configured policy")
			return nil
		}
		return err
	}
	return nil
}

func (ip *InstallationProcess) applyWarmupDefaults() {
	if ip.Script.WarmupTimeout <= 0 {
		ip.Script.WarmupTimeout = 300
	}
	if ip.Script.PostWarmupTimeout <= 0 {
		ip.Script.PostWarmupTimeout = 120
	}
	if strings.TrimSpace(ip.Script.WarmupFailurePolicy) == "" {
		ip.Script.WarmupFailurePolicy = warmupPolicyFail
	}
}

func (ip *InstallationProcess) runWarmupAttempt(ctx context.Context, attempt int) error {
	name := ip.Server.ID() + "_warmup"
	ip.Server.StartInstallPhase(InstallPhaseWarming, name, filepath.Join(config.Get().System.LogDirectory, "install", ip.Server.ID()+"-warmup.log"))

	if err := ip.removeNamedContainer(ctx, name); err != nil {
		return err
	}

	runCtx, cancel := context.WithTimeout(ctx, time.Duration(ip.Script.WarmupTimeout)*time.Second)
	defer cancel()

	id, err := ip.createLifecycleContainer(runCtx, name, "server_warmup", nil)
	if err != nil {
		return errors.Wrap(err, "warmup: create container")
	}
	ip.Server.SetInstallContainer(id)

	if err := ip.client.ContainerStart(runCtx, id, container.StartOptions{}); err != nil {
		return errors.Wrap(err, "warmup: start container")
	}
	ip.Server.SetInstallStatus("running")

	err = ip.waitForWarmupSignal(runCtx, id)
	if err != nil {
		_ = ip.writeLifecycleContainerLog(context.Background(), id, ip.Server.installLogPath(), "Warmup failed")
		_ = ip.removeNamedContainer(context.Background(), name)
		return err
	}

	_ = ip.stopWarmupContainer(context.Background(), id)
	_ = ip.writeLifecycleContainerLog(context.Background(), id, ip.Server.installLogPath(), "Warmup completed")
	_ = ip.removeNamedContainer(context.Background(), name)
	ip.Server.SetInstallStatus("exited")
	var code int64
	ip.Server.SetInstallExitCode(code)
	ip.Server.Log().WithField("attempt", attempt).Info("server warmup completed")
	return nil
}

func (ip *InstallationProcess) runPostWarmup(ctx context.Context) error {
	name := ip.Server.ID() + "_post_warmup"
	ip.Server.StartInstallPhase(InstallPhasePostWarmup, name, filepath.Join(config.Get().System.LogDirectory, "install", ip.Server.ID()+"-post-warmup.log"))

	if err := ip.removeNamedContainer(ctx, name); err != nil {
		return err
	}

	runCtx, cancel := context.WithTimeout(ctx, time.Duration(ip.Script.PostWarmupTimeout)*time.Second)
	defer cancel()

	cmd := []string{"/bin/sh", "-lc", ip.Script.PostWarmupScript}
	id, err := ip.createLifecycleContainer(runCtx, name, "server_post_warmup", cmd)
	if err != nil {
		return errors.Wrap(err, "post-warmup: create container")
	}
	ip.Server.SetInstallContainer(id)

	if err := ip.client.ContainerStart(runCtx, id, container.StartOptions{}); err != nil {
		return errors.Wrap(err, "post-warmup: start container")
	}
	ip.Server.SetInstallStatus("running")

	status, err := ip.waitForContainerExit(runCtx, id)
	if err != nil {
		_ = ip.writeLifecycleContainerLog(context.Background(), id, ip.Server.installLogPath(), "Post-warmup failed")
		_ = ip.removeNamedContainer(context.Background(), name)
		return err
	}
	ip.Server.SetInstallExitCode(status)
	ip.Server.SetInstallStatus("exited")

	_ = ip.writeLifecycleContainerLog(context.Background(), id, ip.Server.installLogPath(), "Post-warmup completed")
	_ = ip.removeNamedContainer(context.Background(), name)
	if status != 0 {
		return errors.Errorf("post-warmup script exited with code %d", status)
	}
	return nil
}

func (ip *InstallationProcess) createLifecycleContainer(ctx context.Context, name, containerType string, cmd []string) (string, error) {
	img := ip.ContainerImage()
	if img == "" {
		return "", errors.New("server has no container image configured")
	}

	containerUser, hostUID, hostGID := ip.installContainerUser()
	baseServerFolder := ip.Server.Filesystem().Overlay().ServerPath
	if err := ip.prepareLifecyclePathWritable(ctx, baseServerFolder, containerUser, hostUID, hostGID); err != nil {
		return "", errors.Wrap(err, "warmup: chown base server folder for container user")
	}

	conf := &container.Config{
		Hostname:     name,
		AttachStdout: true,
		AttachStderr: true,
		AttachStdin:  true,
		OpenStdin:    true,
		Tty:          true,
		User:         containerUser,
		WorkingDir:   "/home/container",
		Image:        strings.TrimPrefix(img, "~"),
		Env:          ip.Server.GetEnvironmentVariables(),
		Labels: map[string]string{
			"Service":       "SLS",
			"ContainerType": containerType,
		},
	}
	if len(cmd) > 0 {
		conf.Entrypoint = cmd[:1]
		conf.Cmd = cmd[1:]
	}

	cfg := config.Get()
	hostConf := &container.HostConfig{
		Mounts:      ip.lifecycleMounts(baseServerFolder),
		Resources:   ip.resourceLimits(),
		DNS:         cfg.Docker.Network.Dns,
		LogConfig:   cfg.Docker.ContainerLogConfig(),
		NetworkMode: container.NetworkMode(cfg.Docker.Network.Mode),
		UsernsMode:  container.UsernsMode(cfg.Docker.UsernsMode),
		Tmpfs: map[string]string{
			"/tmp": "rw,exec,nosuid,size=" + strconv.Itoa(int(cfg.Docker.TmpfsSize)) + "M",
		},
	}

	r, err := ip.client.ContainerCreate(ctx, conf, hostConf, nil, nil, name)
	if err != nil {
		return "", err
	}
	return r.ID, nil
}

func (ip *InstallationProcess) lifecycleMounts(baseServerFolder string) []mount.Mount {
	mounts := []mount.Mount{
		{
			Target:   "/home/container",
			Source:   baseServerFolder,
			Type:     mount.TypeBind,
			ReadOnly: false,
		},
	}
	for _, m := range append(ip.Server.customMounts(), ip.Server.volumeMounts()...) {
		mounts = append(mounts, mount.Mount{
			Target:   m.Target,
			Source:   m.Source,
			Type:     mount.TypeBind,
			ReadOnly: m.ReadOnly,
		})
	}
	return mounts
}

func (ip *InstallationProcess) prepareLifecyclePathWritable(ctx context.Context, path, containerUser string, hostUID, hostGID int) error {
	if err := chownRecursiveTo(path, hostUID, hostGID); err != nil {
		return err
	}

	name := ip.Server.ID() + "_permissions"
	if err := ip.removeNamedContainer(ctx, name); err != nil {
		return err
	}

	cmd := fmt.Sprintf("chown -R %s /home/container && chmod -R u+rwX /home/container", containerUser)
	conf := &container.Config{
		Hostname:   name,
		Image:      strings.TrimPrefix(ip.ContainerImage(), "~"),
		User:       "0:0",
		Entrypoint: []string{"/bin/sh"},
		Cmd:        []string{"-lc", cmd},
		Labels: map[string]string{
			"Service":       "SLS",
			"ContainerType": "server_lifecycle_permissions",
		},
	}
	hostConf := &container.HostConfig{
		Mounts: []mount.Mount{
			{
				Target:   "/home/container",
				Source:   path,
				Type:     mount.TypeBind,
				ReadOnly: false,
			},
		},
	}

	r, err := ip.client.ContainerCreate(ctx, conf, hostConf, nil, nil, name)
	if err != nil {
		return err
	}
	defer ip.removeNamedContainer(context.Background(), name)

	if err := ip.client.ContainerStart(ctx, r.ID, container.StartOptions{}); err != nil {
		return err
	}
	status, err := ip.waitForContainerExit(ctx, r.ID)
	if err != nil {
		return err
	}
	if status != 0 {
		return errors.Errorf("lifecycle permission helper exited with code %d", status)
	}
	return nil
}

func (ip *InstallationProcess) waitForWarmupSignal(ctx context.Context, id string) error {
	reader, err := ip.client.ContainerLogs(ctx, id, container.LogsOptions{
		ShowStdout: true,
		ShowStderr: true,
		Follow:     true,
	})
	if err != nil {
		return err
	}
	defer reader.Close()

	exitCode := make(chan int64, 1)
	exitErr := make(chan error, 1)
	go func() {
		code, err := ip.waitForContainerExit(ctx, id)
		if err != nil {
			exitErr <- err
			return
		}
		exitCode <- code
	}()

	matched := make(chan struct{}, 1)
	scanErr := make(chan error, 1)
	go func() {
		scanner := bufio.NewScanner(reader)
		for scanner.Scan() {
			line := scanner.Bytes()
			ip.Server.Sink(system.InstallSink).Push(append([]byte(nil), line...))
			if ip.matchesStartupSignal(line) {
				matched <- struct{}{}
				return
			}
		}
		if err := scanner.Err(); err != nil && !errors.Is(err, context.Canceled) {
			scanErr <- err
		}
	}()

	select {
	case <-matched:
		return nil
	case code := <-exitCode:
		return errors.Errorf("warmup container exited before online signal with code %d", code)
	case err := <-exitErr:
		return err
	case err := <-scanErr:
		return err
	case <-ctx.Done():
		return errors.Wrap(ctx.Err(), "warmup timed out before online signal")
	}
}

func (ip *InstallationProcess) matchesStartupSignal(line []byte) bool {
	processConfiguration := ip.Server.ProcessConfiguration()
	v := append([]byte(nil), line...)
	if processConfiguration.Startup.StripAnsi {
		v = stripAnsiRegex.ReplaceAll(v, []byte(""))
	}
	for _, matcher := range processConfiguration.Startup.Done {
		if matcher.Matches(v) {
			return true
		}
	}
	return false
}

func (ip *InstallationProcess) waitForContainerExit(ctx context.Context, id string) (int64, error) {
	sChan, eChan := ip.client.ContainerWait(ctx, id, container.WaitConditionNotRunning)
	select {
	case err := <-eChan:
		return 0, err
	case res := <-sChan:
		return res.StatusCode, nil
	case <-ctx.Done():
		return 0, ctx.Err()
	}
}

func (ip *InstallationProcess) stopWarmupContainer(ctx context.Context, id string) error {
	stop := ip.Server.ProcessConfiguration().Stop
	if stop.Type == remote.ProcessStopCommand && strings.TrimSpace(stop.Value) != "" {
		if err := ip.sendContainerCommand(ctx, id, stop.Value); err == nil {
			if _, err := ip.waitForContainerExitWithTimeout(ctx, id, 30*time.Second); err == nil {
				return nil
			}
		}
	}

	if stop.Type == remote.ProcessStopSignal && strings.TrimSpace(stop.Value) != "" {
		if err := ip.client.ContainerKill(ctx, id, strings.ToUpper(stop.Value)); err == nil {
			if _, err := ip.waitForContainerExitWithTimeout(ctx, id, 30*time.Second); err == nil {
				return nil
			}
		}
	}

	timeout := 30
	if err := ip.client.ContainerStop(ctx, id, container.StopOptions{Timeout: &timeout}); err != nil && !client.IsErrNotFound(err) {
		return err
	}
	return nil
}

func (ip *InstallationProcess) sendContainerCommand(ctx context.Context, id, command string) error {
	resp, err := ip.client.ContainerAttach(ctx, id, container.AttachOptions{
		Stdin:  true,
		Stream: true,
	})
	if err != nil {
		return err
	}
	defer resp.Close()

	_, err = io.WriteString(resp.Conn, command+"\n")
	return err
}

func (ip *InstallationProcess) waitForContainerExitWithTimeout(ctx context.Context, id string, timeout time.Duration) (int64, error) {
	waitCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	return ip.waitForContainerExit(waitCtx, id)
}

func (ip *InstallationProcess) removeNamedContainer(ctx context.Context, name string) error {
	err := ip.client.ContainerRemove(ctx, name, container.RemoveOptions{
		RemoveVolumes: true,
		Force:         true,
	})
	if err != nil && !client.IsErrNotFound(err) && !errdefs.IsNotFound(err) {
		return err
	}
	return nil
}

func (ip *InstallationProcess) writeLifecycleContainerLog(ctx context.Context, id, path, title string) error {
	reader, err := ip.client.ContainerLogs(ctx, id, container.LogsOptions{
		ShowStdout: true,
		ShowStderr: true,
		Follow:     false,
	})
	if err != nil {
		return err
	}
	defer reader.Close()

	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o600)
	if err != nil {
		return err
	}
	defer f.Close()

	_, _ = io.WriteString(f, title+"\n\n")
	_, err = io.Copy(f, reader)
	return err
}
