package auth

import (
	"context"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/grokify/coreforge/identity/apikey"
	"github.com/pkg/errors"
)

const CachedKeyTTL = 5 * time.Minute

// CachedStore is an in memory store for API keys that allows quick and efficient lookups.
// Keys are loaded from the database on demand. All operations update both the database
// and the cache, and are only considered successful if both succeed.
type CachedStore struct {
	cache map[string]*cachedEntry // cache by prefix
	mu    sync.RWMutex

	// How long the key can be cached before checking the database again
	// This really only exists to handle cases where the key is directly updated in the database outside of this system
	// all internal manipulation will immediately update the key in the database and cache
	ttl time.Duration
}

type cachedEntry struct {
	key     *apikey.APIKey
	hash    string
	expires time.Time
}

// Creates a new api key store
func NewCachedStore(ttl time.Duration) *CachedStore {
	return &CachedStore{
		cache: make(map[string]*cachedEntry),
		ttl:   ttl,
	}
}

// Create: store in DB and cache
func (c *CachedStore) Create(ctx context.Context, key *apikey.APIKey, keyHash string) error {
	if err := StoreKey(key, keyHash); err != nil {
		return errors.Wrap(err, "failed to store API key in DB")
	}

	// store in the cache
	c.mu.Lock()
	c.cache[key.Prefix] = &cachedEntry{
		key:     key,
		hash:    keyHash,
		expires: time.Now().Add(c.ttl),
	}
	c.mu.Unlock()

	return nil
}

// Get by Prefix
func (c *CachedStore) GetByPrefix(ctx context.Context, prefix string) (*apikey.APIKey, string, error) {
	// check cache first
	c.mu.RLock()
	if entry, ok := c.cache[prefix]; ok && time.Now().Before(entry.expires) {
		c.mu.RUnlock()
		return entry.key, entry.hash, nil
	}
	c.mu.RUnlock()

	// fallback to DB
	key, hash, err := GetByPrefix(prefix)
	if err != nil {
		return nil, "", err
	}

	// cache only this single key
	c.mu.Lock()
	c.cache[prefix] = &cachedEntry{
		key:     key,
		hash:    hash,
		expires: time.Now().Add(c.ttl),
	}
	c.mu.Unlock()

	return key, hash, nil
}

// Get by ID
func (c *CachedStore) GetByID(ctx context.Context, id uuid.UUID) (*apikey.APIKey, error) {
	// check cache
	c.mu.RLock()
	for _, entry := range c.cache {
		if entry.key.ID == id && time.Now().Before(entry.expires) {
			c.mu.RUnlock()
			return entry.key, nil
		}
	}
	c.mu.RUnlock()

	// fallback to DB
	key, _, err := GetByID(id)
	if err != nil {
		return nil, err
	}

	// cache only this single key
	c.mu.Lock()
	c.cache[key.Prefix] = &cachedEntry{
		key:     key,
		hash:    "", // hash can be included if needed
		expires: time.Now().Add(c.ttl),
	}
	c.mu.Unlock()

	return key, nil
}

// List by OwnerID
// Always queried from the database
func (c *CachedStore) ListByOwner(ctx context.Context, ownerID uuid.UUID) ([]*apikey.APIKey, error) {
	return ListByOwner(ownerID)
}

// List by OrganizationID
// Always queried from the database
func (c *CachedStore) ListByOrganization(ctx context.Context, orgID uuid.UUID) ([]*apikey.APIKey, error) {
	return ListByOrganization(orgID)
}

// Update key
func (c *CachedStore) Update(ctx context.Context, key *apikey.APIKey) error {
	if err := UpdateKey(key); err != nil {
		return err
	}

	// update cache if this key is already cached
	c.mu.Lock()
	if entry, ok := c.cache[key.Prefix]; ok && entry.key.ID == key.ID {
		entry.key = key
		entry.expires = time.Now().Add(c.ttl)
	}
	c.mu.Unlock()

	return nil
}

// Delete key
func (c *CachedStore) Delete(ctx context.Context, id uuid.UUID) error {
	key, _, err := GetByID(id)
	if err != nil {
		// nothing to delete
		return nil
	}

	if err := DeleteKey(id); err != nil {
		return err
	}

	// remove from cache if present
	c.mu.Lock()
	delete(c.cache, key.Prefix)
	c.mu.Unlock()

	return nil
}

// Update LastUsed
func (c *CachedStore) UpdateLastUsed(ctx context.Context, id uuid.UUID, ip string) error {
	if err := UpdateLastUsed(id, ip); err != nil {
		return err
	}

	// update cache if this key is cached
	c.mu.Lock()
	for _, entry := range c.cache {
		if entry.key.ID == id {
			now := time.Now()
			entry.key.LastUsedAt = &now
			entry.key.LastUsedIP = ip
			entry.expires = time.Now().Add(c.ttl)
			break
		}
	}
	c.mu.Unlock()

	return nil
}
