package remote

import (
	"context"
	"net/http"
	"sync"
	"time"

	"github.com/apex/log"
	"protoxon.com/sls/daemon/models"
)

type Client interface {
	Register(ctx context.Context) error
	Heartbeat(ctx context.Context)
	Disconnect(ctx context.Context)
	StatusUpdate(ctx context.Context, status string, id string) error // Sends a server status update
	CrashReport(ctx context.Context, data CrashData, id string) error // Sends a server crash report
	ServerDeleted(ctx context.Context, id string) error               // Sends a server deleted event
	GetServers(context context.Context, perPage int) ([]models.ServerConfigurationResponse, error)
	GetServerConfiguration(ctx context.Context, uuid string) (models.ServerConfigurationResponse, error)
	GetServerInstallInfo(ctx context.Context, uuid string) (models.InstallationScript, error)
	SetOnConnected(callback func(context.Context))
}

type client struct {
	httpClient  *http.Client
	baseUrl     string
	token       string
	maxAttempts int
	ctx         context.Context
	cancel      context.CancelFunc
	onConnected func(context.Context)
	mu          sync.RWMutex
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

	// Attempt to register on startup, but don't block if it fails
	// Heartbeats will retry registration if needed
	go func() {
		registerCtx, registerCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer registerCancel()
		if err := c.Register(registerCtx); err != nil {
			// Registration failed, but heartbeats will handle retrying
			log.WithError(err).Debug("initial registration attempt failed, will retry on heartbeat")
		}
	}()

	c.StartHeartbeats()
	return &c
}

// SetOnConnected sets the callback to be executed when the node successfully connects.
// This can be called after the client is created to avoid circular dependencies.
func (c *client) SetOnConnected(callback func(context.Context)) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.onConnected = callback
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
