package remote

import (
	"fmt"
	"net/http"

	"emperror.dev/errors"
)

// RequestErrors is a legacy wire format from older APIs (errors array).
type RequestErrors struct {
	Errors []RequestError `json:"errors"`
}

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
		return fmt.Sprintf("Error response from Remote: %s %s: %s (hint: %s) (HTTP/%d)", re.Code, re.Status, re.Detail, re.Hint, c)
	}
	return fmt.Sprintf("Error response from Remote: %s %s: %s (HTTP/%d)", re.Code, re.Status, re.Detail, c)
}

// StatusCode returns the status code of the response.
func (re *RequestError) StatusCode() int {
	return re.response.StatusCode
}

type SftpInvalidCredentialsError struct{}

func (ice SftpInvalidCredentialsError) Error() string {
	return "the credentials provided were invalid"
}
