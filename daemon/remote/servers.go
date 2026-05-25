package remote

import (
	"context"
	"fmt"
	"strconv"
	"sync"

	"golang.org/x/sync/errgroup"
	"protoxon.com/sls/daemon/models"
)

const (
	ProcessStopCommand = "command"
	ProcessStopSignal  = "signal"
)

func (c *client) StatusUpdate(ctx context.Context, status string, id string) error {
	_, err := Post[d](c, ctx, "/internal/event/servers/"+id+"/status", status)
	return err
}

func (c *client) CrashReport(ctx context.Context, data CrashData, id string) error {
	_, err := Post[d](c, ctx, "/internal/event/servers/"+id+"/crash", data)
	return err
}

func (c *client) ServerDeleted(ctx context.Context, id string) error {
	_, err := Post[d](c, ctx, "/internal/event/servers/"+id+"/deleted", nil)
	return err
}

// GetServers returns all the servers that are present on the Protocube that are part of this node making
// parallel API calls to the endpoint if more than one page of servers is
// returned.
func (c *client) GetServers(ctx context.Context, limit int) ([]models.ServerConfiguration, error) {
	servers, meta, err := c.getServersPaged(ctx, 0, limit)
	if err != nil {
		return nil, err
	}

	var mu sync.Mutex
	if meta.LastPage > 1 {
		g, ctx := errgroup.WithContext(ctx)
		for page := meta.CurrentPage + 1; page <= meta.LastPage; page++ {
			page := page
			g.Go(func() error {
				ps, _, err := c.getServersPaged(ctx, int(page), limit)
				if err != nil {
					return err
				}
				mu.Lock()
				servers = append(servers, ps...)
				mu.Unlock()
				return nil
			})
		}
		if err := g.Wait(); err != nil {
			return nil, err
		}
	}

	return servers, nil
}

// getServersPaged returns a subset of servers from the Protocube API using the
// pagination query parameters.
func (c *client) getServersPaged(ctx context.Context, page, limit int) ([]models.ServerConfiguration, Pagination, error) {
	type r struct {
		Data []models.ServerConfiguration `json:"data"`
		Meta Pagination                   `json:"meta"`
	}
	res, err := Get[r](c, ctx, "/internal/servers", q{
		"page":     strconv.Itoa(page),
		"per_page": strconv.Itoa(limit),
	})
	if err != nil {
		return nil, Pagination{}, err
	}
	return res.Data, res.Meta, nil
}

func (c *client) GetServerConfiguration(ctx context.Context, uuid string) (models.ServerConfiguration, error) {
	var config models.ServerConfiguration
	res, err := Get[models.ServerConfiguration](c, ctx, fmt.Sprintf("/internal/servers/%s", uuid), nil)
	if err != nil {
		return config, err
	}
	return res, nil
}
