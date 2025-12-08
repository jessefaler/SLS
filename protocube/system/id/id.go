package id

import (
	"math/rand"
	"strings"
	"time"
)

const length = 6
const charset = "abcdefhikmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

var randGen = rand.New(rand.NewSource(time.Now().UnixNano()))

// New generates a new random id
// 6 characters in length
func New() string {
	var sb strings.Builder
	for i := 0; i < length; i++ {
		// Pick a random character from the charset
		randomIndex := randGen.Intn(len(charset))
		sb.WriteByte(charset[randomIndex])
	}

	// Return the generated id
	return sb.String()
}
