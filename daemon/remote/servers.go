package remote

import (
	"context"
	"strconv"
	"sync"

	"golang.org/x/sync/errgroup"
)

const (
	ProcessStopCommand    = "command"
	ProcessStopSignal     = "signal"
	ProcessStopNativeStop = "stop"
)

func (c *client) StatusUpdate(ctx context.Context, status string, id string) error {
	_, err := Post[d](c, ctx, "/event/servers/"+id+"/status", status)
	return err
}

// GetServers returns all the servers that are present on the Node making
// parallel API calls to the endpoint if more than one page of servers is
// returned.
func (c *client) GetServers(ctx context.Context, limit int) ([]RawServerData, error) {
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

// getServersPaged returns a subset of servers from the Node's API using the
// pagination query parameters.
func (c *client) getServersPaged(ctx context.Context, page, limit int) ([]RawServerData, Pagination, error) {
	var r struct {
		Data []RawServerData `json:"data"`
		Meta Pagination      `json:"meta"`
	}

	res, err := c.Get(ctx, "/servers", q{
		"page":     strconv.Itoa(page),
		"per_page": strconv.Itoa(limit),
	})
	if err != nil {
		return nil, r.Meta, err
	}
	defer res.Body.Close()
	if err := res.BindJSON(&r); err != nil {
		return nil, r.Meta, err
	}
	return r.Data, r.Meta, nil
}

// CreateServer makes a request to create a server on the node using the
// blueprint id
func (c *client) CreateServer(ctx context.Context, blueprintID string) (CreateServerResponse, error) {
	var result CreateServerResponse

	body := CreateServerRequest{
		BlueprintID: blueprintID,
	}

	res, err := c.Post(ctx, "/servers", body)
	if err != nil {
		return result, err
	}
	defer res.Body.Close()

	if err := res.BindJSON(&result); err != nil {
		return result, err
	}

	return result, nil
}
