package middleware

import (
	"fmt"
	"sync"
	"sync/atomic"
	"time"

	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"golang.org/x/time/rate"
	"protoxon.com/sls/protocube/api/router/httperror"
)

// Client rate limiter
//
// Provides per-client rate limiting using ip based limiters
// Includes automatic cleanup of inactive clients to prevent memory growth.

const (
	requestsPerSecond = 20
	burst             = 50
	cleanupInterval   = 5 * time.Minute
	clientTTL         = 20 * time.Minute
)

var clients sync.Map

type clientEntry struct {
	limiter  *rate.Limiter
	lastSeen atomic.Value
}

// getLimiterEntry returns or creates a client entry
func getLimiter(clientID string) *clientEntry {
	now := time.Now()
	if v, ok := clients.Load(clientID); ok {
		entry := v.(*clientEntry)
		entry.lastSeen.Store(now)
		return entry
	}

	limiter := rate.NewLimiter(rate.Limit(requestsPerSecond), burst)
	entry := &clientEntry{limiter: limiter}
	entry.lastSeen.Store(now)
	clients.Store(clientID, entry)
	return entry
}

// RateLimiter Provides per-client rate limiting using ip based limiters
func RateLimiter() gin.HandlerFunc {
	return func(c *gin.Context) {
		clientID := c.ClientIP()
		entry := getLimiter(clientID)

		// Set headers
		SetRateLimitHeaders(c, entry)

		// Check limit
		if !entry.limiter.Allow() {
			log.Debug("Rate limit exceeded for client " + clientID)
			retry := RetryAfterSeconds(entry.limiter)
			c.Header("Retry-After", fmt.Sprintf("%d", retry))
			httperror.AbortTooManyRequests(c, "rate_limit_exceeded", "Too many requests. Please try again later.", retry)
			return
		}

		c.Next()
	}
}

// SetRateLimitHeaders sets all rate limit headers for the client
func SetRateLimitHeaders(c *gin.Context, entry *clientEntry) {
	limiter := entry.limiter
	now := time.Now()

	c.Header("X-RateLimit-Limit", fmt.Sprintf("%d", requestsPerSecond*burst))
	c.Header("X-RateLimit-Remaining", fmt.Sprintf("%d", RemainingTokens(limiter)))
	c.Header("X-RateLimit-Reset", fmt.Sprintf("%.3f", ResetTimestamp(limiter, now)))
}

// RemainingTokens calculates remaining auth for X-RateLimit-Remaining
func RemainingTokens(limiter *rate.Limiter) int {
	tokens := limiter.Tokens()
	if tokens < 0 {
		return 0
	}
	return int(tokens)
}

// ResetTimestamp calculates the approximate reset time for X-RateLimit-Reset.
//
// Some clients expect this header to be a Unix timestamp in SECONDS (optionally
// with a fractional part) which they multiply by 1000 to get milliseconds
// and compare against System.currentTimeMillis(). If the value is truncated
// to whole seconds, the resulting reset time in milliseconds may appear to
// already be in the past, causing the client to ignore the rate limit.
//
// To prevent this, we return a floating-point Unix timestamp with millisecond
// precision, ensuring that after multiplying by 1000, the reset time is safely
// in the future.
func ResetTimestamp(limiter *rate.Limiter, now time.Time) float64 {
	base := float64(now.UnixNano()) / 1e9

	// If we still have tokens, the reset is effectively "now".
	if limiter.Tokens() >= 1 {
		return base
	}

	// Otherwise, approximate next token refill time by adding the retry‑after
	// interval (in seconds).
	return base + float64(RetryAfterSeconds(limiter))
}

// RetryAfterSeconds returns seconds until next token is available
func RetryAfterSeconds(limiter *rate.Limiter) int {
	tokens := limiter.Tokens()
	if tokens >= 1 {
		return 0
	}
	return int(time.Second.Seconds())
}

// ClientRateLimiterCleanUp launches a background goroutine that periodically
// removes inactive client rate limiters from memory.
//
// It runs an infinite loop that sleeps for 'cleanupInterval' between passes.
// On each iteration, it computes a cutoff time ('now - clientTTL') and deletes
// any client entry whose 'lastSeen' timestamp is older than that cutoff.
//
// This prevents unbounded growth of the 'clients' map by automatically
// cleaning up rate limiters for clients that have been inactive for a while.
//
// This much be called when starting the HTTP server
func ClientRateLimiterCleanUp() {
	go func() {
		for {
			time.Sleep(cleanupInterval)
			cutoff := time.Now().Add(-clientTTL)
			clients.Range(func(k, v interface{}) bool {
				entry := v.(*clientEntry)
				t, ok := entry.lastSeen.Load().(time.Time)
				if ok && t.Before(cutoff) {
					clients.Delete(k)
				}
				return true
			})
		}
	}()
}
