package server

import (
	"context"
	"fmt"
	"html/template"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/containerd/errdefs"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/mount"
	"github.com/docker/docker/client"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/environment/docker"
	"protoxon.com/sls/daemon/remote"
	"protoxon.com/sls/daemon/system"
)

// Install executes the installation stack for a server process. Bubbles any
// errors up to the calling function which should handle contacting protocube to
// notify it of the server state.
//
// Install runs the installation process. Pass a context with timeout (e.g. from
// server.InstallLockTimeout) so the install can be aborted; when the context is
// cancelled the install stops and callers should remove the server folder and release the lock.
func (s *Server) Install(ctx context.Context) error {
	return s.install(ctx, false)
}

func (s *Server) install(ctx context.Context, reinstall bool) error {
	var err error
	if !s.Config().SkipInstallScripts {
		// Send the start event so protocube can automatically update.
		s.Events().Publish(InstallStartedEvent, "")

		err = s.internalInstall(ctx)
	} else {
		s.Log().Info("server configured to skip running installation scripts for this software, not executing process")
	}

	// Notify protocube of install state. On failure, do this in the background so we return
	// (and the caller can log the error) immediately instead of blocking on a slow/timeout HTTP call.
	successful := err == nil
	s.Log().WithField("was_successful", successful).Debug("notifying protocube of server install state")
	notifyProtocube := func() {
		if serr := s.SyncInstallState(successful, reinstall); serr != nil {
			s.Log().WithField("was_successful", successful).WithField("error", serr).Warn("failed to notify protocube of server install state")
		}
	}
	if successful {
		notifyProtocube()
	} else {
		go notifyProtocube()
	}

	// Ensure that the server is marked as offline at this point, otherwise you
	// end up with a blank value which is a bit confusing.
	s.Environment.SetState(environment.ProcessOfflineState)

	// Push an event to the websocket, so we can auto-refresh the information in
	// protocube once the installation is completed.
	s.Events().Publish(InstallCompletedEvent, "")

	return errors.WithStackIf(err)
}

// Reinstall reinstalls a server's software by utilizing the installation script
// for the server software. This does not touch any existing files for the server,
// other than what the script modifies.
func (s *Server) Reinstall() error {
	if s.Environment.State() != environment.ProcessOfflineState {
		s.Log().Debug("waiting for server instance to enter a stopped state")
		if err := s.Environment.WaitForStop(s.Context(), time.Second*10, true); err != nil {
			return errors.WrapIf(err, "install: failed to stop running environment")
		}
	}

	s.Log().Info("syncing server state with remote source before executing re-installation process")
	if err := s.Sync(); err != nil {
		return errors.WrapIf(err, "install: failed to sync server state with Protocube")
	}

	return s.install(s.Context(), true)
}

// Internal installation function used to simplify reporting back to Protocube.
func (s *Server) internalInstall(ctx context.Context) error {
	script, err := s.client.GetInstallationScript(ctx, s.ID())
	if err != nil {
		return err
	}
	p, err := NewInstallationProcess(s, &script)
	if err != nil {
		return err
	}

	s.Log().Info("beginning installation process for server")
	if err := p.Run(ctx); err != nil {
		return err
	}

	s.Log().Info("completed installation process for server")
	return nil
}

type InstallationProcess struct {
	Server     *Server
	Script     *remote.InstallationScript
	client     *client.Client
	installCtx context.Context // cancelled when install timeout or server is deleted
}

// NewInstallationProcess returns a new installation process struct that will be
// used to create containers and otherwise perform installation commands for a
// server.
func NewInstallationProcess(s *Server, script *remote.InstallationScript) (*InstallationProcess, error) {
	proc := &InstallationProcess{
		Script: script,
		Server: s,
	}

	if c, err := environment.Docker(); err != nil {
		return nil, err
	} else {
		proc.client = c
	}

	return proc, nil
}

// IsInstalling returns if the server is actively running the installation
// process by checking the status of the installer lock.
//func (s *Server) IsInstalling() bool {
//	return s.installing.Load()
//}

func (ip *InstallationProcess) installContext() context.Context {
	if ip.installCtx != nil {
		return ip.installCtx
	}
	return ip.Server.Context()
}

// RemoveContainer removes the installation container for the server.
func (ip *InstallationProcess) RemoveContainer() error {
	err := ip.client.ContainerRemove(ip.installContext(), ip.Server.ID()+"_installer", container.RemoveOptions{
		RemoveVolumes: true,
		Force:         true,
	})
	if err != nil && !client.IsErrNotFound(err) {
		return err
	}
	return nil
}

// Run runs the installation process, this is done as in a background thread.
// This will configure the required environment, and then spin up the
// installation container. Once the container finishes installing the results
// are stored in an installation log in the server's configuration directory.
// The context is used to cancel the install (e.g. on timeout).
func (ip *InstallationProcess) Run(ctx context.Context) error {
	ip.installCtx = ctx
	ip.Server.Log().Debug("acquiring installation process lock")
	//if !ip.Server.installing.SwapIf(true) {
	//	return errors.New("install: cannot obtain installation lock")
	//}

	// We now have an exclusive lock on this installation process. Ensure that whenever this
	// process is finished that the semaphore is released so that other processes and be executed
	// without encountering a wait timeout.
	defer func() {
		ip.Server.Log().Debug("releasing installation process lock")
		//ip.Server.installing.Store(false)
	}()

	if err := ip.BeforeExecute(); err != nil {
		return err
	}

	cID, err := ip.Execute()
	if err != nil {
		_ = ip.RemoveContainer()
		return err
	}

	// If this step fails, log a warning but don't exit out of the process. This is completely
	// internal to the daemon's functionality, and does not affect the status of the server itself.
	if err := ip.AfterExecute(cID); err != nil {
		ip.Server.Log().WithField("error", err).Warn("failed to complete after-execute step of installation process")
	}

	return nil
}

// Returns the location of the temporary data for the installation process.
func (ip *InstallationProcess) tempDir() string {
	return filepath.Join(config.Get().System.TmpDirectory, ip.Server.ID())
}

// Writes the installation script to a temporary file on the host machine so that it
// can be properly mounted into the installation container and then executed.
func (ip *InstallationProcess) writeScriptToDisk() error {
	// Make sure the temp directory root exists before trying to make a directory within it. The
	// os.TempDir call expects this base to exist, it won't create it for you.
	// 0o755 so container user (often non-root) can traverse and read install.sh (fixes exit 126).
	if err := os.MkdirAll(ip.tempDir(), 0o755); err != nil {
		return errors.WithMessage(err, "could not create temporary directory for install process")
	}
	f, err := os.OpenFile(filepath.Join(ip.tempDir(), "install.sh"), os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0o644)
	if err != nil {
		return errors.WithMessage(err, "failed to write server installation script to disk before mount")
	}
	defer f.Close()
	if _, err := io.Copy(f, strings.NewReader(strings.ReplaceAll(ip.Script.Script, "\r\n", "\n"))); err != nil {
		return err
	}
	return nil
}

// Pulls the docker image to be used for the installation container (server's image).
// Behavior follows docker.image_pull_policy
func (ip *InstallationProcess) pullInstallationImage() error {
	img := ip.ContainerImage()
	if img == "" {
		return errors.New("server has no container image configured")
	}

	// Images prefixed with ~ are local images that we do not try to pull.
	if strings.HasPrefix(img, "~") {
		return nil
	}

	policy := config.Get().Docker.ImagePullPolicy

	pull := func() error {
		pullCtx, cancelPull := context.WithTimeout(ip.installContext(), 15*time.Minute)
		defer cancelPull()
		return docker.PullImageWithOfflineFallback(pullCtx, ip.client, img, nil)
	}

	switch policy {
	case config.ImagePullPolicyNever:
		inspectCtx, cancel := context.WithTimeout(ip.installContext(), 30*time.Second)
		defer cancel()
		if _, err := ip.client.ImageInspect(inspectCtx, img); errdefs.IsNotFound(err) {
			return errors.Errorf("installation image %q is not present locally (docker.image_pull_policy is Never)", img)
		} else if err != nil {
			return errors.Wrap(err, "failed to inspect installation image")
		}
		return nil

	case config.ImagePullPolicyIfNotPresent:
		inspectCtx, cancel := context.WithTimeout(ip.installContext(), 30*time.Second)
		defer cancel()
		if ok, err := docker.ImageExistsLocally(inspectCtx, ip.client, img); err != nil {
			return err
		} else if ok {
			log.WithField("image", img).Debug("installation image already present locally; skipping pull")
			return nil
		}
		return pull()

	case config.ImagePullPolicySchedule:
		docker.RegisterScheduledPullImage(img)
		inspectCtx, cancel := context.WithTimeout(ip.installContext(), 30*time.Second)
		defer cancel()
		if ok, err := docker.ImageExistsLocally(inspectCtx, ip.client, img); err != nil {
			return err
		} else if ok {
			log.WithField("image", img).Debug("installation image already present locally; skipping pull (scheduled)")
			return nil
		}
		return pull()

	case config.ImagePullPolicyAlways:
		fallthrough
	default:
		return pull()
	}
}

// BeforeExecute runs before the container is executed. This pulls down the
// required docker container image as well as writes the installation script to
// the disk. This process is executed in an async manner, if either one fails
// the error is returned.
func (ip *InstallationProcess) BeforeExecute() error {
	if err := ip.writeScriptToDisk(); err != nil {
		return errors.WithMessage(err, "failed to write installation script to disk")
	}
	if err := ip.pullInstallationImage(); err != nil {
		return errors.WithMessage(err, "failed to pull updated installation container image for server")
	}
	if err := ip.RemoveContainer(); err != nil {
		return errors.WithMessage(err, "failed to remove existing install container for server")
	}
	return nil
}

// ContainerImage returns the Docker image used for the installation container (the server's image).
func (ip *InstallationProcess) ContainerImage() string {
	return ip.Server.Config().Container.Image
}

// installEntrypoint returns entrypoint for the install container so we run bash + script
// instead of the image's default (e.g. java -jar server.jar), which fixes exit 126.
func (ip *InstallationProcess) installEntrypoint() []string {
	ep := strings.TrimSpace(ip.Script.Entrypoint)
	switch ep {
	case "bash", "/bin/bash":
		return []string{"/bin/bash"}
	case "sh", "/bin/sh":
		return []string{"/bin/sh"}
	default:
		return []string{ep}
	}
}

// installContainerUser returns the container User string (uid:gid) and the host uid/gid
// to chown the base server folder so the install process can write. Matches server container user.
func (ip *InstallationProcess) installContainerUser() (containerUser string, hostUID, hostGID int) {
	cfg := config.Get()
	if cfg.System.User.Rootless.Enabled {
		containerUser = fmt.Sprintf("%d:%d", cfg.System.User.Rootless.ContainerUID, cfg.System.User.Rootless.ContainerGID)
		hostUID, hostGID = os.Getuid(), os.Getgid()
	} else {
		hostUID, hostGID = cfg.System.User.Uid, cfg.System.User.Gid
		containerUser = strconv.Itoa(hostUID) + ":" + strconv.Itoa(hostGID)
	}
	return containerUser, hostUID, hostGID
}

// chownRecursiveTo sets ownership of path and its contents to uid:gid.
func chownRecursiveTo(path string, uid, gid int) error {
	return filepath.WalkDir(path, func(p string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if err := os.Chown(p, uid, gid); err != nil {
			return errors.Wrapf(err, "chown %s", p)
		}
		return nil
	})
}

// GetLogPath returns the log path for the installation process.
func (ip *InstallationProcess) GetLogPath() string {
	return filepath.Join(config.Get().System.LogDirectory, "/install", ip.Server.ID()+".log")
}

// writeFailedInstallLog writes container stdout/stderr to the install log when the script exits non-zero.
func (ip *InstallationProcess) writeFailedInstallLog(ctx context.Context, containerID string, statusCode int64) {
	reader, err := ip.client.ContainerLogs(ctx, containerID, container.LogsOptions{
		ShowStdout: true,
		ShowStderr: true,
		Follow:     false,
	})
	if err != nil {
		ip.Server.Log().WithField("error", err).Warn("could not fetch install container logs after failure")
		return
	}
	defer reader.Close()
	logPath := ip.GetLogPath()
	if err := os.MkdirAll(filepath.Dir(logPath), 0o755); err != nil {
		ip.Server.Log().WithField("error", err).Warn("could not create install log directory")
		return
	}
	f, err := os.OpenFile(logPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o600)
	if err != nil {
		ip.Server.Log().WithField("error", err).Warn("could not write install log after failure")
		return
	}
	defer f.Close()
	_, _ = io.WriteString(f, "Installation failed (exit code "+strconv.FormatInt(statusCode, 10)+"). Container output:\n\n")
	_, _ = io.Copy(f, reader)
	ip.Server.Log().WithField("path", logPath).Info("install failure log written for debugging")
}

// AfterExecute cleans up after the execution of the installation process.
// This grabs the logs from the process to store in the server configuration
// directory, and then destroys the associated installation container.
func (ip *InstallationProcess) AfterExecute(containerId string) error {
	defer ip.RemoveContainer()

	ip.Server.Log().WithField("container_id", containerId).Debug("pulling installation logs for server")
	reader, err := ip.client.ContainerLogs(ip.installContext(), containerId, container.LogsOptions{
		ShowStdout: true,
		ShowStderr: true,
		Follow:     false,
	})

	if err != nil && !client.IsErrNotFound(err) {
		return err
	}

	f, err := os.OpenFile(ip.GetLogPath(), os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o600)
	if err != nil {
		return err
	}
	defer f.Close()

	// We write the contents of the container output to a more "permanent" file so that they
	// can be referenced after this container is deleted. We'll also include the environment
	// variables passed into the container to make debugging things a little easier.
	ip.Server.Log().WithField("path", ip.GetLogPath()).Debug("writing most recent installation logs to disk")

	tmpl, err := template.New("header").Parse(`SLS Server Installation Log

|
| Details
| ------------------------------
  Server UUID:          {{.Server.ID}}
  Container Image:      {{.ContainerImage}}
  Container Entrypoint: {{.Script.Entrypoint}}

|
| Environment Variables
| ------------------------------
{{ range $key, $value := .Server.GetEnvironmentVariables }}  {{ $value }}
{{ end }}

|
| Script Output
| ------------------------------
`)
	if err != nil {
		return err
	}

	if err := tmpl.Execute(f, ip); err != nil {
		return err
	}

	if _, err := io.Copy(f, reader); err != nil {
		return err
	}

	return nil
}

// Execute executes the installation process inside a specially created docker
// container.
func (ip *InstallationProcess) Execute() (string, error) {
	// Create a child context that is canceled once this function is done running. This
	// will also be canceled if the install context is canceled (timeout or server deleted).
	ctx, cancel := context.WithCancel(ip.installContext())
	defer cancel()

	img := ip.ContainerImage()
	if img == "" {
		return "", errors.New("server has no container image configured")
	}

	// Resolve container user and chown base folder so the install script can write.
	// Mount the base server folder at /home/container so the install script
	// writes directly to the base on the host.
	baseServerFolder := ip.Server.Filesystem().Overlay().ServerPath
	containerUser, hostUID, hostGID := ip.installContainerUser()
	if err := chownRecursiveTo(baseServerFolder, hostUID, hostGID); err != nil {
		return "", errors.Wrapf(err, "install: chown base server folder for container user")
	}

	conf := &container.Config{
		Hostname:     "installer",
		AttachStdout: true,
		AttachStderr: true,
		AttachStdin:  true,
		OpenStdin:    true,
		Tty:          true,
		User:         containerUser,
		Entrypoint:   ip.installEntrypoint(),
		Cmd:          []string{"/mnt/install/install.sh"},
		Image:        img,
		Env:          ip.Server.GetEnvironmentVariables(),
		Labels: map[string]string{
			"Service":       "SLS",
			"ContainerType": "server_installer",
		},
	}

	cfg := config.Get()
	tmpfsSize := strconv.Itoa(int(cfg.Docker.TmpfsSize))
	hostConf := &container.HostConfig{
		Mounts: []mount.Mount{
			{
				Target:   "/home/container",
				Source:   baseServerFolder,
				Type:     mount.TypeBind,
				ReadOnly: false,
			},
			{
				Target:   "/mnt/install",
				Source:   ip.tempDir(),
				Type:     mount.TypeBind,
				ReadOnly: false,
			},
		},
		Resources: ip.resourceLimits(),
		Tmpfs: map[string]string{
			"/tmp": "rw,exec,nosuid,size=" + tmpfsSize + "M",
		},
		DNS:         cfg.Docker.Network.Dns,
		LogConfig:   cfg.Docker.ContainerLogConfig(),
		NetworkMode: container.NetworkMode(cfg.Docker.Network.Mode),
		UsernsMode:  container.UsernsMode(cfg.Docker.UsernsMode),
	}

	// Base server folder is created by the caller (onBeforeStart) before Install() is called.
	ip.Server.Log().WithField("install_script", ip.tempDir()+"/install.sh").WithField("base_folder", baseServerFolder).Info("creating install container for server process")
	// Remove the temporary directory when the installation process finishes for this server container.
	defer func() {
		if err := os.RemoveAll(ip.tempDir()); err != nil {
			if !os.IsNotExist(err) {
				ip.Server.Log().WithField("error", err).Warn("failed to remove temporary data directory after install process")
			}
		}
	}()

	r, err := ip.client.ContainerCreate(ctx, conf, hostConf, nil, nil, ip.Server.ID()+"_installer")
	if err != nil {
		return "", err
	}

	ip.Server.Log().WithField("container_id", r.ID).Info("running installation script for server in container")
	if err := ip.client.ContainerStart(ctx, r.ID, container.StartOptions{}); err != nil {
		return "", err
	}

	// Process the install event in the background by listening to the stream output until the
	// container has stopped, at which point we'll disconnect from it.
	//
	// If there is an error during the streaming output just report it and do nothing else, the
	// install can still run, the console just won't have any output.
	go func(id string) {
		ip.Server.Events().Publish(DaemonMessageEvent, "Starting installation process, this could take a few minutes...")
		if err := ip.StreamOutput(ctx, id); err != nil {
			ip.Server.Log().WithField("error", err).Warn("error connecting to server install stream output")
		}
	}(r.ID)

	sChan, eChan := ip.client.ContainerWait(ctx, r.ID, container.WaitConditionNotRunning)
	select {
	case err := <-eChan:
		// Once the container has stopped running we can mark the install process as being completed.
		if err == nil {
			ip.Server.Events().Publish(DaemonMessageEvent, "Installation process completed.")
		} else {
			return "", err
		}
	case res := <-sChan:
		if res.StatusCode != 0 {
			ip.writeFailedInstallLog(ctx, r.ID, res.StatusCode)
			return "", errors.Errorf("install script exited with code %d (see install log for output)", res.StatusCode)
		}
		ip.Server.Events().Publish(DaemonMessageEvent, "Installation process completed.")
	}

	return r.ID, nil
}

// StreamOutput streams the output of the installation process to a log file in
// the server configuration directory, as well as to a websocket listener
func (ip *InstallationProcess) StreamOutput(ctx context.Context, id string) error {
	opts := container.LogsOptions{ShowStdout: true, ShowStderr: true, Follow: true}
	reader, err := ip.client.ContainerLogs(ctx, id, opts)
	if err != nil {
		return err
	}
	defer reader.Close()

	err = system.ScanReader(reader, ip.Server.Sink(system.InstallSink).Push)
	if err != nil && !errors.Is(err, context.Canceled) {
		ip.Server.Log().WithFields(log.Fields{"container_id": id, "error": err}).Warn("error processing install output lines")
	}
	return nil
}

// resourceLimits returns resource limits for the installation container. This
// looks at the globally defined install container limits and attempts to use
// the higher of the two (defined limits & server limits). This allows for servers
// with super low limits (e.g. Discord bots with 128Mb of memory) to perform more
// intensive installation processes if needed.
//
// This also avoids a server with limits such as 4GB of memory from accidentally
// consuming 2-5x the defined limits during the install process and causing
// system instability.
func (ip *InstallationProcess) resourceLimits() container.Resources {
	limits := config.Get().Docker.InstallerLimits

	// Create a copy of the configuration, so we're not accidentally making
	// changes to the underlying server build data.
	c := *ip.Server.Config()
	cfg := c.Build
	if cfg.MemoryLimit < limits.Memory {
		cfg.MemoryLimit = limits.Memory
	}
	// Only apply the CPU limit if neither one is currently set to unlimited. If the
	// installer CPU limit is unlimited don't even waste time with the logic, just
	// set the config to unlimited for this.
	if limits.Cpu == 0 {
		cfg.CpuLimit = 0
	} else if cfg.CpuLimit != 0 && cfg.CpuLimit < limits.Cpu {
		cfg.CpuLimit = limits.Cpu
	}

	resources := cfg.AsContainerResources()
	// Explicitly remove the PID limits for the installation container. These scripts are
	// defined at an administrative level and users can't manually execute things like a
	// fork bomb during this process.
	resources.PidsLimit = nil

	return resources
}

// SyncInstallState makes an HTTP request to the Protocube instance notifying it that
// the server has completed the installation process, and what the state of the
// server is.
func (s *Server) SyncInstallState(successful, reinstall bool) error {
	return s.client.SetInstallationStatus(s.Context(), s.ID(), remote.InstallStatusRequest{
		Successful: successful,
		Reinstall:  reinstall,
	})
}
