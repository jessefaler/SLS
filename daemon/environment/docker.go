package environment

import (
	"context"
	"net"
	"strconv"
	"sync"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/docker/docker/api/types/network"
	"github.com/docker/docker/client"
	"protoxon.com/sls/daemon/config"
)

var (
	_conce  sync.Once
	_client *client.Client
)

// Docker returns a docker client to be used throughout the codebase. Once a
// client has been created it will be returned for all subsequent calls to this
// function.
func Docker() (*client.Client, error) {
	var err error
	_conce.Do(func() {
		_client, err = client.NewClientWithOpts(client.FromEnv, client.WithAPIVersionNegotiation())
	})
	return _client, errors.Wrap(err, "environment/docker: could not create client")
}

// ConfigureDocker configures the required network for the docker environment.
func ConfigureDocker(ctx context.Context) error {
	// Ensure the required docker network exists on the system.
	cli, err := Docker()
	if err != nil {
		return err
	}

	nw := config.Get().Docker.Network
	resource, err := cli.NetworkInspect(ctx, nw.Name, network.InspectOptions{})
	if err != nil {
		if !client.IsErrNotFound(err) {
			return err
		}

		log.Info("creating missing sls0 interface, this could take a few seconds...")
		if err := createDockerNetwork(ctx, cli); err != nil {
			return err
		}
		// Re-inspect so we have the created network's details for config.Update below.
		resource, err = cli.NetworkInspect(ctx, nw.Name, network.InspectOptions{})
		if err != nil {
			return errors.Wrap(err, "environment/docker: failed to inspect newly created network")
		}
	}

	config.Update(func(c *config.Configuration) {
		c.Docker.Network.Driver = resource.Driver
		switch c.Docker.Network.Driver {
		case "host":
			c.Docker.Network.Interface = "127.0.0.1"
			c.Docker.Network.ISPN = false
		case "overlay":
			fallthrough
		case "weavemesh":
			c.Docker.Network.Interface = ""
			c.Docker.Network.ISPN = true
		default:
			c.Docker.Network.ISPN = false
			// Set Interface from the network's gateway (e.g. when Docker auto-assigned the subnet).
			if len(resource.IPAM.Config) > 0 {
				for _, cfg := range resource.IPAM.Config {
					if cfg.Gateway != "" && net.ParseIP(cfg.Gateway).To4() != nil {
						c.Docker.Network.Interface = cfg.Gateway
						break
					}
				}
			}
		}
	})
	return nil
}

// Creates a new network on the machine if one does not exist already.
// If docker.network.interfaces.v4.subnet is empty, Docker is left to choose a free
// subnet to avoid "Pool overlaps with other one on this address space" errors.
func createDockerNetwork(ctx context.Context, cli *client.Client) error {
	nw := config.Get().Docker.Network
	enableIPv6 := true
	ipamConfig := make([]network.IPAMConfig, 0, 2)
	if nw.Interfaces.V4.Subnet != "" {
		ipamConfig = append(ipamConfig, network.IPAMConfig{
			Subnet:  nw.Interfaces.V4.Subnet,
			Gateway: nw.Interfaces.V4.Gateway,
		})
	}
	if nw.Interfaces.V6.Subnet != "" {
		ipamConfig = append(ipamConfig, network.IPAMConfig{
			Subnet:  nw.Interfaces.V6.Subnet,
			Gateway: nw.Interfaces.V6.Gateway,
		})
	}
	createOpts := network.CreateOptions{
		Driver:     nw.Driver,
		EnableIPv6: &enableIPv6,
		Internal:   nw.IsInternal,
		IPAM: &network.IPAM{
			Driver: "default",
			Config: ipamConfig,
		},
		Options: map[string]string{
			"encryption": "false",
			"com.docker.network.bridge.default_bridge":       "false",
			"com.docker.network.bridge.enable_icc":           strconv.FormatBool(nw.EnableICC),
			"com.docker.network.bridge.enable_ip_masquerade": "true",
			"com.docker.network.bridge.host_binding_ipv4":    "0.0.0.0",
			"com.docker.network.bridge.name":                 "sls",
			"com.docker.network.driver.mtu":                  strconv.FormatInt(nw.NetworkMTU, 10),
		},
	}
	_, err := cli.NetworkCreate(ctx, nw.Name, createOpts)
	if err != nil {
		return err
	}
	// When we used an explicit V4 subnet, set Interface now; otherwise ConfigureDocker sets it from the inspect.
	if nw.Driver != "host" && nw.Driver != "overlay" && nw.Driver != "weavemesh" && nw.Interfaces.V4.Subnet != "" {
		config.Update(func(c *config.Configuration) {
			c.Docker.Network.Interface = c.Docker.Network.Interfaces.V4.Gateway
		})
	}
	return nil
}
