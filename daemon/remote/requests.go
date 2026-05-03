package remote

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/cenkalti/backoff/v4"
	"protoxon.com/sls/daemon/api/router/httperror"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/system"
)

type Requests interface {
	Post(ctx context.Context, path string, data interface{}) (*Response, error)
	Get(ctx context.Context, path string, query q) (*Response, error)
}

// Post sends a POST request using the provided client,
// with the given context, path, and data.
// The path is prepended with protocube's node api endpoint /api/nodes/{nodeId}
// It then unmarshal's the JSON response into the specified type T.
// Returns the result and any error encountered during the process.
func Post[T any](request Requests, ctx context.Context, path string, data interface{}) (T, error) {
	var zero T
	nodeId := config.Get().Uuid
	path = "/api/nodes/" + nodeId + path
	res, err := request.Post(ctx, path, data)
	if err != nil {
		return zero, err
	}
	result, err := BindJSON[T](res)
	if err != nil {
		return zero, errors.Wrap(err, "failed to bind json")
	}
	_ = res.Body.Close()
	return result, nil
}

// Get sends a GET request using the provided client,
// with the given context, path, and query parameters.
// The path is prepended with protocube's node api endpoint /api/nodes/{nodeId}
// It then unmarshal's the JSON response into the specified type T.
// Returns the result and any error encountered during the process.
func Get[T any](client Requests, ctx context.Context, path string, query q) (T, error) {
	var zero T
	nodeId := config.Get().Uuid
	path = "/api/nodes/" + nodeId + path
	res, err := client.Get(ctx, path, query)
	if err != nil {
		return zero, err
	}
	result, err := BindJSON[T](res)
	if err != nil {
		return zero, errors.Wrap(err, "failed to bind json")
	}
	_ = res.Body.Close()
	return result, nil
}

// Get executes a HTTP GET request.
func (c *client) Get(ctx context.Context, path string, query q) (*Response, error) {
	return c.request(ctx, http.MethodGet, path, nil, func(r *http.Request) {
		q := r.URL.Query()
		for k, v := range query {
			q.Set(k, v)
		}
		r.URL.RawQuery = q.Encode()
	})
}

// Post executes an HTTP POST request.
func (c *client) Post(ctx context.Context, path string, data interface{}) (*Response, error) {
	b, err := json.Marshal(data)
	if err != nil {
		return nil, err
	}
	return c.request(ctx, http.MethodPost, path, bytes.NewBuffer(b))
}

// requestOnce creates a http request and executes it once. Prefer request()
// over this method when possible. It appends the path to the endpoint of the
// client and adds the authentication token to the request.
func (c *client) requestOnce(ctx context.Context, method, path string, body io.Reader, opts ...func(r *http.Request)) (*Response, error) {
	req, err := http.NewRequestWithContext(ctx, method, c.baseUrl+path, body)
	if err != nil {
		return nil, err
	}

	req.Header.Set("User-Agent", fmt.Sprintf("SLS/v%s (id:%s)", system.Version, config.Get().Uuid))
	req.Header.Set("Accept", "application/vnd.sls.v1+json")
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", c.token))

	// Call all opts functions to allow modifying the request
	for _, o := range opts {
		o(req)
	}

	debugLogRequest(req)

	res, err := c.httpClient.Do(req)
	return &Response{res}, err
}

// request executes an HTTP request against the Protocube API. If there is an error
// encountered with the request it will be retried using an exponential backoff.
// If the error returned from Protocube is due to API throttling or because there
// are invalid authentication credentials provided the request will _not_ be
// retried by the backoff.
//
// This function automatically appends the path to the current client endpoint
// and adds the required authentication headers to the request that is being
// created. Errors returned will be of the RequestError type if there was some
// type of response from the API that can be parsed.
//
// If an unregistered error is returned this function will attempt to register
// before retrying the original request
func (c *client) request(ctx context.Context, method, path string, body *bytes.Buffer, opts ...func(r *http.Request)) (*Response, error) {
	var res *Response
	err := backoff.Retry(func() error {
		var b bytes.Buffer
		if body != nil {
			// We have to create a copy of the body, otherwise attempting this request again will
			// send no data if there was initially a body since the "requestOnce" method will read
			// the whole buffer, thus leaving it empty at the end.
			if _, err := b.Write(body.Bytes()); err != nil {
				return backoff.Permanent(errors.Wrap(err, "http: failed to copy body buffer"))
			}
		}
		r, err := c.requestOnce(ctx, method, path, &b, opts...)
		if err != nil {
			if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
				return backoff.Permanent(err)
			}
			return errors.WrapIf(err, "http: request creation failed")
		}
		res = r
		if r.HasError() {
			// Close the request body after returning the error to free up resources.
			defer r.Body.Close()

			// Check if it's an unregistered error
			if isUnregistered(r) {
				// Attempt to register the node
				if err := c.Register(ctx); err != nil {
					return backoff.Permanent(err) // fail permanently if registration fails
				}
				return errors.New("unregistered: registered node, retrying request")
			}

			// Don't keep attempting to access this endpoint if the response is a 4XX
			// level error which indicates a client mistake. Only retry when the error
			// is due to a server issue (5XX error).
			if r.StatusCode >= 400 && r.StatusCode < 500 {
				return backoff.Permanent(r.Error())
			}
			return r.Error()
		}
		return nil
	}, c.backoff(ctx))
	if err != nil {
		if v, ok := err.(*backoff.PermanentError); ok {
			return nil, v.Unwrap()
		}
		return nil, err
	}
	return res, nil
}

// Checks if the response indicates this node is unregistered
func isUnregistered(resp *Response) bool {
	// Check for 412 Precondition Failed (explicit unregistered error)
	if resp.StatusCode == http.StatusPreconditionFailed {
		// Read the body into memory
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return false
		}

		// After reading, reset the Body so it can be read again if needed
		resp.Body = io.NopCloser(bytes.NewReader(body))

		var apiErr httperror.Error
		if err := json.Unmarshal(body, &apiErr); err != nil {
			return false
		}
		if apiErr.Detail == "Unregistered" {
			return true
		}
	}

	// Also check for 404 Not Found on node-specific routes, as this indicates
	// the node doesn't exist and needs to be registered
	if resp.StatusCode == http.StatusNotFound {
		// Check if the request path contains "/api/nodes/" which indicates
		// this is a node-specific route that requires the node to exist
		if resp.Request != nil && resp.Request.URL != nil {
			path := resp.Request.URL.Path
			// If it's a 404 on a node route (but not the register route itself),
			// treat it as unregistered
			if strings.Contains(path, "/api/nodes/") && !strings.Contains(path, "/register") {
				return true
			}
		}
	}

	return false
}

// backoff returns an exponential backoff function for use with remote API
// requests. This will allow an API call to be executed approximately 10 times
// before it is finally reported back as an error.
//
// This allows for issues with DNS resolution, or rare race conditions due to
// slower SQL queries on protocube to potentially self-resolve without just
// immediately failing the first request. The example below shows the amount of
// time that has elapsed between each call to the handler when an error is
// returned. You can tweak these values as needed to get the effect you desire.
//
// If maxAttempts is a value greater than 0 the backoff will be capped at a total
// number of executions, or the MaxElapsedTime, whichever comes first.
//
// call(): 0s
// call(): 552.330144ms
// call(): 1.63271196s
// call(): 2.94284202s
// call(): 4.525234711s
// call(): 6.865723375s
// call(): 11.37194223s
// call(): 14.593421816s
// call(): 20.202045293s
// call(): 27.36567952s <-- Stops here as MaxElapsedTime is 30 seconds
func (c *client) backoff(ctx context.Context) backoff.BackOffContext {
	b := backoff.NewExponentialBackOff()
	b.MaxInterval = time.Second * 12
	b.MaxElapsedTime = time.Second * 30
	if c.maxAttempts > 0 {
		return backoff.WithContext(backoff.WithMaxRetries(b, uint64(c.maxAttempts)), ctx)
	}
	return backoff.WithContext(b, ctx)
}

// Response is a custom response type that allows for commonly used error
// handling and response parsing from protocube API. This just embeds the normal
// HTTP response from Go and we attach a few helper functions to it.
type Response struct {
	*http.Response
}

// HasError determines if the API call encountered an error. If no request has
// been made the response will be false. This function will evaluate to true if
// the response code is anything 300 or higher.
func (r *Response) HasError() bool {
	if r.Response == nil {
		return false
	}

	return r.StatusCode >= 300 || r.StatusCode < 200
}

// Reads the body from the response and returns it, then replaces it on the response
// so that it can be read again later. This does not close the response body, so any
// functions calling this should be sure to manually defer a Body.Close() call.
func (r *Response) Read() ([]byte, error) {
	var b []byte
	if r.Response == nil {
		return nil, errors.New("remote: attempting to read missing response")
	}
	if r.Response.Body != nil {
		b, _ = io.ReadAll(r.Response.Body)
	}
	r.Response.Body = io.NopCloser(bytes.NewBuffer(b))
	return b, nil
}

// BindJSON binds a given interface with the data returned in the response. This
// is a shortcut for calling Read and then manually calling json.Unmarshal on
// the raw bytes.
func (r *Response) BindJSON(v interface{}) error {
	b, err := r.Read()
	if err != nil {
		return err
	}
	if err := json.Unmarshal(b, &v); err != nil {
		return errors.Wrap(err, "remote: could not unmarshal response")
	}
	return nil
}

// BindJSON unmarshal the response body into a value of type T.
// Returns the unmarshalled value and an error if reading or unmarshalling fails.
func BindJSON[T any](r *Response) (T, error) {
	var result T
	b, err := r.Read()
	if err != nil {
		return result, err
	}
	if len(b) == 0 {
		return result, nil
	}
	if err := json.Unmarshal(b, &result); err != nil {
		return result, errors.Wrap(err, "could not unmarshal response")
	}
	return result, nil
}

// Returns the first error message from the API call as a string. The error
// message will be formatted similar to the below example. If there is no error
// that can be parsed out of the API you'll still get a RequestError returned
// but the RequestError.Code will be "_MissingResponseCode".
//
// HttpNotFoundException: The requested resource does not exist. (HTTP/404)
func (r *Response) Error() error {
	if !r.HasError() {
		return nil
	}

	b, err := r.Read()
	if err != nil {
		return errors.WithStackDepth(err, 1)
	}

	e := &RequestError{
		Code:   "_MissingResponseCode",
		Status: http.StatusText(r.StatusCode),
		Detail: "No error response returned from API endpoint.",
	}

	var apiErr httperror.Error
	if json.Unmarshal(b, &apiErr) == nil && (apiErr.Code != "" || apiErr.Detail != "" || apiErr.Hint != "") {
		e.Code = apiErr.Code
		e.Status = apiErr.Status
		e.Detail = apiErr.Detail
		e.Hint = apiErr.Hint
		if e.Code == "" {
			e.Code = strconv.Itoa(r.StatusCode)
		}
		if e.Status == "" {
			e.Status = http.StatusText(r.StatusCode)
		}
	}

	e.response = r.Response

	return errors.WithStackDepth(e, 1)
}

// Logs the request into the debug log with all of the important request bits.
// The authorization key will be cleaned up before being output.
func debugLogRequest(req *http.Request) {
	if l, ok := log.Log.(*log.Logger); ok && l.Level != log.DebugLevel {
		return
	}
	headers := make(map[string][]string)
	for k, v := range req.Header {
		if k != "Authorization" || len(v) == 0 || len(v[0]) == 0 {
			headers[k] = v
			continue
		}

		headers[k] = []string{"(redacted)"}
	}

	log.WithFields(log.Fields{
		"method":   req.Method,
		"endpoint": req.URL.String(),
		"headers":  headers,
	}).Debug("making request to external HTTP endpoint")
}
