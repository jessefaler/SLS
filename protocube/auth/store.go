package auth

import (
	"strings"
	"sync"

	"github.com/pkg/errors"
	"protoxon.com/sls/protocube/internal/database"
)

// todo store hashed versions of keys instead of plain text and verify a key using its hash

type TokenStore struct {
	mutex  sync.RWMutex
	tokens map[string]Token
}

func (ts *TokenStore) put(token Token) error {
	ts.mutex.Lock()
	defer ts.mutex.Unlock()
	ts.tokens[token.Id] = token
	return saveToken(token)
}

// saveToken stores a single token record in the database.
func saveToken(token Token) error {
	if err := database.Instance().Create(&token).Error; err != nil {
		return errors.Wrap(err, "failed to save api key to database")
	}
	return nil
}

// LoadAllTokens loads all token records from the database.
func LoadAllTokens() (*TokenStore, error) {
	err := database.Instance().AutoMigrate(&Token{})
	if err != nil {
		return nil, errors.Wrap(err, "failed to auto migrate auth")
	}
	var records []Token
	if err := database.Instance().Find(&records).Error; err != nil {
		return nil, errors.Wrap(err, "failed to load api auth from database")
	}

	store := &TokenStore{
		tokens: make(map[string]Token),
	}

	for _, r := range records {
		store.tokens[r.Id] = r
	}

	return store, nil
}

// Verify checks if a given composite key exists in the token store
// and matches the provided KeyType. Returns (true, nil) if valid,
// otherwise (false, error) with the reason.
func (ts *TokenStore) Verify(compositeKey string, keyType KeyType) (bool, error) {
	// Split the key: expected format "SLS_ID_KEY"
	parts := strings.SplitN(compositeKey, "_", 3)
	if len(parts) != 3 {
		return false, errors.New("invalid key format")
	}

	id := parts[1] // extract the ID part

	ts.mutex.RLock()
	defer ts.mutex.RUnlock()

	token, exists := ts.tokens[id]
	if !exists {
		return false, errors.New("invalid token")
	}

	if token.KeyType != keyType {
		return false, errors.Errorf(
			"invalid key type: got %s key, expected %s key",
			token.KeyType.String(),
			keyType.String(),
		)
	}

	return true, nil
}
