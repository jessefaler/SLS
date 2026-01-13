package remote

import (
	"context"
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
