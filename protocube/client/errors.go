package client

import (
	"context"
	"fmt"
	"net/http"

	"emperror.dev/errors"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/httperror"
)

var ErrNodeUnavailable = errors.New("node unavailable")

// RequestError is returned when a remote HTTP API responds with an error body.
type RequestError struct {
	response *http.Response
	Code     string `json:"code"`
	Status   string `json:"status"`
	Detail   string `json:"detail"`
	Hint     string `json:"hint,omitempty"`
}

// IsRequestError checks if the given error is of the RequestError type.
func IsRequestError(err error) bool {
	var rerr *RequestError
	if err == nil {
		return false
	}
	return errors.As(err, &rerr)
}

// AsRequestError transforms the error into a RequestError if it is currently
// one, checking the wrap status from the other error handlers. If the error
// is not a RequestError nil is returned.
func AsRequestError(err error) *RequestError {
	if err == nil {
		return nil
	}
	var rerr *RequestError
	if errors.As(err, &rerr) {
		return rerr
	}
	return nil
}

// Error returns the error response in a string form that can be more easily
// consumed.
func (re *RequestError) Error() string {
	c := 0
	if re.response != nil {
		c = re.response.StatusCode
	}
	if re.Hint != "" {
		return fmt.Sprintf("Error response from Node: %s %s: %s (hint: %s) (HTTP/%d)", re.Code, re.Status, re.Detail, re.Hint, c)
	}
	return fmt.Sprintf("Error response from Node: %s %s: %s (HTTP/%d)", re.Code, re.Status, re.Detail, c)
}

// StatusCode returns the status code of the response.
func (re *RequestError) StatusCode() int {
	return re.response.StatusCode
}

// HandleError sends an appropriate HTTP response for errors returned by a node request.
// It first checks for a structured RequestError, then maps context.DeadlineExceeded to 504,
// context.Canceled to 408, and falls back to 502 Bad Gateway for all other errors.
func HandleError(c *gin.Context, err error) {
	if re := AsRequestError(err); re != nil {
		c.JSON(re.StatusCode(), httperror.FromParts(re.StatusCode(), re.Code, re.Status, re.Detail, re.Hint))
		return
	}
	switch {
	case errors.Is(err, ErrNodeUnavailable):
		httperror.JSON(c, http.StatusServiceUnavailable, err.Error(), "No node is available for this request.")
	case errors.Is(err, context.DeadlineExceeded):
		httperror.JSON(c, http.StatusGatewayTimeout, err.Error(), "Remote node request timed out.")
	case errors.Is(err, context.Canceled):
		httperror.JSON(c, http.StatusRequestTimeout, err.Error(), "Request context was canceled.")
	default:
		httperror.JSON(c, http.StatusBadGateway, err.Error(), "")
	}
}
