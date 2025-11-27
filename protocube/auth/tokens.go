package auth

import (
	"crypto/rand"
	"fmt"
	"math/big"

	"emperror.dev/errors"
)

type Token struct {
	Id      string `gorm:"primaryKey"`
	Key     string
	KeyType KeyType
}

type KeyType int

const (
	Application KeyType = iota
	Node
)

func (k KeyType) String() string {
	return [...]string{"Application", "Node"}[k]
}

const (
	prefix      = "SLS"
	idLength    = 6
	tokenLength = 16
)

// Example Generated Key:
//       ID		   Token
// SLS_aB3dE1_XyZ9w8RtGh3QkLp

var charset = []byte("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

// randomString generates a random string of length n using letters and numbers
func randomString(n int) (string, error) {
	result := make([]byte, n)
	for i := 0; i < n; i++ {
		index, err := rand.Int(rand.Reader, big.NewInt(int64(len(charset))))
		if err != nil {
			return "", err
		}
		result[i] = charset[index.Int64()]
	}
	return string(result), nil
}

// GenerateToken generates an api key in the format SLS_ID_KEY
func GenerateToken() (Token, error) {
	id, err := randomString(idLength)
	if err != nil {
		return Token{}, err
	}
	key, err := randomString(tokenLength)
	if err != nil {
		return Token{}, err
	}

	token := Token{
		Id:  id,
		Key: key,
	}
	return token, nil
}

func (t *Token) String() string {
	return fmt.Sprintf("%s_%s_%s", prefix, t.Id, t.Key)
}

func (ts *TokenStore) NewKey(keyType KeyType) (Token, error) {
	token, err := GenerateToken()
	if err != nil {
		return Token{}, errors.Wrap(err, "Failed to generate api key")
	}

	token.KeyType = keyType
	if err := ts.put(token); err != nil {
		return Token{}, errors.Wrap(err, "Failed to store api key")
	}

	return token, nil
}
