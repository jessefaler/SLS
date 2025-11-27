package remote

import (
	"context"
	"net/http"
	"time"
)

type Client interface {
	Register(ctx context.Context) error
	Heartbeat(ctx context.Context)
	Disconnect(ctx context.Context)
	StatusUpdate(ctx context.Context, status string, id string) error // Sends a server status update
}

type client struct {
	httpClient  *http.Client
	baseUrl     string
	token       string
	maxAttempts int
	ctx         context.Context
	cancel      context.CancelFunc
}

// New returns a new HTTP request client that is used for making authenticated
// requests to the node
func New(base string, opts ...ClientOption) Client {
	ctx, cancel := context.WithCancel(context.Background())
	c := client{
		baseUrl: base,
		httpClient: &http.Client{
			Timeout: time.Second * 15,
		},
		maxAttempts: 0,
		ctx:         ctx,
		cancel:      cancel,
	}
	for _, opt := range opts {
		opt(&c)
	}
	c.StartHeartbeats()
	return &c
}

// WithCredentials sets the credentials to use when making request to the remote
// API endpoint.
func WithCredentials(token string) ClientOption {
	return func(c *client) {
		c.token = token
	}
}

// WithHttpClient sets the underlying HTTP client instance to use when making
// requests to protocube API.
func WithHttpClient(httpClient *http.Client) ClientOption {
	return func(c *client) {
		c.httpClient = httpClient
	}
}
