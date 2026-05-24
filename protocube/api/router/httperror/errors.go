// Package httperror defines the standard JSON error shape for Protocube HTTP APIs.
package httperror

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
)

// Error is the standard JSON error body for Protocube API responses.
// Code and Status are derived from the HTTP status code. Detail holds internal
// diagnostics; Hint is optional and safe to show to end users when non-empty.
type Error struct {
	Code   string `json:"code"`
	Status string `json:"status"`
	Detail string `json:"detail"`
	Hint   string `json:"hint,omitempty"`
}

// ErrorWithRequestID adds an optional request correlation ID for support.
type ErrorWithRequestID struct {
	Error
	RequestID string `json:"request_id,omitempty"`
}

// New returns an Error with code and status text derived from httpStatus.
func New(httpStatus int, detail, hint string) Error {
	return Error{
		Code:   strconv.Itoa(httpStatus),
		Status: http.StatusText(httpStatus),
		Detail: detail,
		Hint:   hint,
	}
}

// FromParts builds an Error, using code and status when both are non-empty;
// otherwise code and status are derived from httpStatus (for proxying upstream errors).
func FromParts(httpStatus int, code, status, detail, hint string) Error {
	if code != "" && status != "" {
		return Error{Code: code, Status: status, Detail: detail, Hint: hint}
	}
	return New(httpStatus, detail, hint)
}

// JSON writes a JSON error response with the given HTTP status.
func JSON(c *gin.Context, httpStatus int, detail, hint string) {
	c.JSON(httpStatus, New(httpStatus, detail, hint))
}

// AbortWithJSON aborts the request with a JSON error body.
func AbortWithJSON(c *gin.Context, httpStatus int, detail, hint string) {
	c.AbortWithStatusJSON(httpStatus, New(httpStatus, detail, hint))
}

// AbortWithRequestID aborts with the standard error fields plus request_id when non-empty.
func AbortWithRequestID(c *gin.Context, httpStatus int, detail, hint, requestID string) {
	body := ErrorWithRequestID{
		Error:     New(httpStatus, detail, hint),
		RequestID: requestID,
	}
	c.AbortWithStatusJSON(httpStatus, body)
}

// TooManyRequests is the standard 429 body including retry_after (seconds).
type TooManyRequests struct {
	Code       string `json:"code"`
	Status     string `json:"status"`
	Detail     string `json:"detail"`
	Hint       string `json:"hint,omitempty"`
	RetryAfter int    `json:"retry_after"`
}

// AbortTooManyRequests aborts with 429 and rate-limit metadata.
func AbortTooManyRequests(c *gin.Context, detail, hint string, retryAfterSec int) {
	e := New(http.StatusTooManyRequests, detail, hint)
	c.AbortWithStatusJSON(http.StatusTooManyRequests, TooManyRequests{
		Code:       e.Code,
		Status:     e.Status,
		Detail:     e.Detail,
		Hint:       e.Hint,
		RetryAfter: retryAfterSec,
	})
}
