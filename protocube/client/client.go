package client

import (
	"net/http"
	"time"
)

// Client serves as the base client shared by all node and server clients.
type Client struct {
	httpClient  *http.Client
	maxAttempts int
}

type ClientOption func(c *Client)

// WithHTTPClient Option: custom HTTP client
func WithHTTPClient(httpClient *http.Client) ClientOption {
	return func(c *Client) {
		c.httpClient = httpClient
	}
}

// WithMaxAttempts Option: retry attempts
func WithMaxAttempts(n int) ClientOption {
	return func(c *Client) {
		c.maxAttempts = n
	}
}

func New(opts ...ClientOption) *Client {
	c := &Client{
		httpClient: &http.Client{
			Timeout: 15 * time.Second,
		},
		maxAttempts: 3,
	}

	for _, opt := range opts {
		opt(c)
	}

	return c
}

// Node creates a client for a specific node.
func (c *Client) Node(nodeId string, baseUrl, token string) NodeClient {
	return &nodeClient{
		client:  c,
		nodeId:  nodeId,
		baseUrl: baseUrl,
		token:   token,
	}
}
