package client

import (
	"context"
	"fmt"
	"net/http"

	"emperror.dev/errors"
	"github.com/gin-gonic/gin"
)

var ErrNodeUnavailable = errors.New("node unavailable")

type RequestErrors struct {
	Errors []RequestError `json:"errors"`
}

type RequestError struct {
	response *http.Response
	Code     string `json:"code"`
	Status   string `json:"status"`
	Detail   string `json:"detail"`
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

	return fmt.Sprintf("Error response from Node: %s: %s (HTTP/%d)", re.Code, re.Detail, c)
}

// StatusCode returns the status code of the response.
func (re *RequestError) StatusCode() int {
	return re.response.StatusCode
}

// HandleError sends an appropriate HTTP response for errors returned by a node request.
// It first checks for structured RequestErrors, then maps context.DeadlineExceeded to 504,
// context.Canceled to 408, and falls back to 502 Bad Gateway for all other errors.
func HandleError(c *gin.Context, err error) {
	if re := AsRequestError(err); re != nil {
		c.JSON(re.StatusCode(), gin.H{
			"code":   re.Code,
			"status": re.Status,
			"detail": re.Detail,
		})
		return
	}
	switch {
	case errors.Is(err, ErrNodeUnavailable):
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": err.Error()})
	case errors.Is(err, context.DeadlineExceeded):
		c.JSON(http.StatusGatewayTimeout, gin.H{"error": "Remote node request timed out"})
	case errors.Is(err, context.Canceled):
		c.JSON(http.StatusRequestTimeout, gin.H{"error": "Request context was canceled"})
	default:
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
	}
}
