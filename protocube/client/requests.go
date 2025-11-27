package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/cenkalti/backoff/v4"
	"protoxon.com/sls/protocube/system"
)

type Requests interface {
	Post(ctx context.Context, path string, data interface{}) (*Response, error)
	Get(ctx context.Context, path string, query q) (*Response, error)
}

// Post sends a POST request using the provided client,
// with the given context, path, and data.
// It then unmarshal's the JSON response into the specified type T.
// Returns the result and any error encountered during the process.
func Post[T any](request Requests, ctx context.Context, path string, data interface{}) (T, error) {
	var zero T
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
// It then unmarshal's the JSON response into the specified type T.
// Returns the result and any error encountered during the process.
func Get[T any](client Requests, ctx context.Context, path string, query q) (T, error) {
	var zero T
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

// Get executes an HTTP GET request.
func (nc *nodeClient) Get(ctx context.Context, path string, query q) (*Response, error) {
	path = "/api" + path // Prepend the api route to the path
	return nc.request(ctx, http.MethodGet, path, nil, func(r *http.Request) {
		q := r.URL.Query()
		for k, v := range query {
			q.Set(k, v)
		}
		r.URL.RawQuery = q.Encode()
	})
}

// Get performs a scoped HTTP GET request to the node's API for this specific server.
// It automatically prefixes the provided path with "/servers/{id}" so callers only
// need to specify the server-relative endpoint.
func (sc *serverClient) Get(ctx context.Context, path string, query q) (*Response, error) {
	route := fmt.Sprintf("/servers/%s%s", sc.id, path)
	return sc.node.Get(ctx, route, query)
}

// Post performs a scoped HTTP POST request to the node's API for this specific server.
// It automatically prefixes the provided path with "/servers/{id}" so callers only
// need to specify the server-relative endpoint.
func (sc *serverClient) Post(ctx context.Context, path string, data interface{}) (*Response, error) {
	route := fmt.Sprintf("/servers/%s%s", sc.id, path)
	return sc.node.Post(ctx, route, data)
}

// Post executes an HTTP POST request.
func (nc *nodeClient) Post(ctx context.Context, path string, data interface{}) (*Response, error) {
	path = "/api" + path // Prepend the api route to the path
	b, err := json.Marshal(data)
	if err != nil {
		return nil, err
	}
	return nc.request(ctx, http.MethodPost, path, bytes.NewBuffer(b))
}

// requestOnce creates a http request and executes it once. Prefer request()
// over this method when possible. It appends the path to the endpoint of the
// Client and adds the authentication token to the request.
func (nc *nodeClient) requestOnce(ctx context.Context, method, path string, body io.Reader, opts ...func(r *http.Request)) (*Response, error) {
	req, err := http.NewRequestWithContext(ctx, method, nc.baseUrl+path, body)
	if err != nil {
		return nil, err
	}

	req.Header.Set("User-Agent", fmt.Sprintf("Protocube/v%s ", system.Version))
	req.Header.Set("Accept", "application/vnd.protocube.v1+json")
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", nc.token))

	// Call all opts functions to allow modifying the request
	for _, o := range opts {
		o(req)
	}

	debugLogRequest(req)

	res, err := nc.client.httpClient.Do(req)
	return &Response{res}, err
}

// request executes an HTTP request against the Node's API. If there is an error
// encountered with the request it will be retried using an exponential backoff.
// If the error returned from the Node is due to API throttling or because there
// are invalid authentication credentials provided the request will _not_ be
// retried by the backoff.
//
// This function automatically appends the path to the current Client endpoint
// and adds the required authentication headers to the request that is being
// created. Errors returned will be of the RequestError type if there was some
// type of response from the API that can be parsed.
func (nc *nodeClient) request(ctx context.Context, method, path string, body *bytes.Buffer, opts ...func(r *http.Request)) (*Response, error) {
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
		r, err := nc.requestOnce(ctx, method, path, &b, opts...)
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
			// Don't keep attempting to access this endpoint if the response is a 4XX
			// level error which indicates a client mistake. Only retry when the error
			// is due to a server issue (5XX error).
			if r.StatusCode >= 400 && r.StatusCode < 500 {
				return backoff.Permanent(r.Error())
			}
			return r.Error()
		}
		return nil
	}, nc.backoff(ctx))
	if err != nil {
		if v, ok := err.(*backoff.PermanentError); ok {
			return nil, v.Unwrap()
		}
		return nil, err
	}
	return res, nil
}

// backoff returns an exponential backoff function for use with remote API
// requests. This will allow an API call to be executed approximately 10 times
// before it is finally reported back as an error.
//
// This allows for issues with DNS resolution, or rare race conditions due to
// slower SQL queries on the Node to potentially self-resolve without just
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
func (nc *nodeClient) backoff(ctx context.Context) backoff.BackOffContext {
	b := backoff.NewExponentialBackOff()
	b.MaxInterval = time.Second * 12
	b.MaxElapsedTime = time.Second * 30
	if nc.client.maxAttempts > 0 {
		return backoff.WithContext(backoff.WithMaxRetries(b, uint64(nc.client.maxAttempts)), ctx)
	}
	return backoff.WithContext(b, ctx)
}

// Response is a custom response type that allows for commonly used error
// handling and response parsing from the Node's API. This just embeds the normal
// HTTP response from Go, and we attach a few helper functions to it.
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
		return nil, errors.New("attempting to read missing response")
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
		return errors.Wrap(err, "could not unmarshal response")
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

	var errs RequestErrors
	_ = r.BindJSON(&errs)

	e := &RequestError{
		Code:   "_MissingResponseCode",
		Status: strconv.Itoa(r.StatusCode),
		Detail: "No error response returned from API endpoint.",
	}
	if len(errs.Errors) > 0 {
		e = &errs.Errors[0]
	} else {
		// Try to parse simple error format: {"error": "error message"}
		var simpleError struct {
			Error string `json:"error"`
		}
		if r.BindJSON(&simpleError) == nil && simpleError.Error != "" {
			e.Code = r.Status
			e.Detail = simpleError.Error
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
