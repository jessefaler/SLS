package client

// A generic type allowing for easy binding use when making requests to API
// endpoints that only expect a singular argument or something that would not
// benefit from being a typed struct.
//
// Inspired by gin.H, same concept.
type d map[string]interface{}

// Same concept as d, but a map of strings, used for querying GET requests.
type q map[string]string
