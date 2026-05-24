package models

import (
	"database/sql/driver"
	"encoding/json"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/grokify/coreforge/identity/apikey"
)

// stored key is the same as apikey.APIKey but with gorm tags and the hash
// so we can correctly store it in the gorm database
type StoredKey struct {
	ID             uuid.UUID          `json:"id" gorm:"type:uuid;primaryKey"`
	Name           string             `json:"name"`
	Prefix         string             `json:"prefix" gorm:"uniqueIndex"`
	OwnerID        uuid.UUID          `json:"owner_id" gorm:"index"`
	OrganizationID *uuid.UUID         `json:"organization_id,omitempty" gorm:"index"`
	Scopes         StringSlice        `json:"scope,omitempty" gorm:"type:text"`
	Description    string             `json:"description,omitempty"`
	Environment    apikey.Environment `json:"environment"`
	ExpiresAt      *time.Time         `json:"expires_at,omitempty"`
	LastUsedAt     *time.Time         `json:"last_used_at,omitempty"`
	LastUsedIP     string             `json:"last_used_ip,omitempty"`
	Revoked        bool               `json:"revoked"`
	RevokedAt      *time.Time         `json:"revoked_at,omitempty"`
	RevokedReason  string             `json:"revoked_reason,omitempty"`
	Metadata       MetadataMap        `json:"metadata,omitempty" gorm:"type:text"`
	CreatedAt      time.Time          `json:"created_at"`
	UpdatedAt      time.Time          `json:"updated_at"`

	// The hash of the key
	Hash string `json:"hash"`
}

type StringSlice []string

func (s *StringSlice) Scan(src any) error {
	if src == nil {
		*s = nil
		return nil
	}
	switch v := src.(type) {
	case string:
		return json.Unmarshal([]byte(v), s)
	case []byte:
		return json.Unmarshal(v, s)
	default:
		return fmt.Errorf("cannot scan %T into StringSlice", src)
	}
}

func (s StringSlice) Value() (driver.Value, error) {
	return json.Marshal(s)
}

type MetadataMap map[string]string

func (m *MetadataMap) Scan(value interface{}) error {
	if value == nil {
		*m = nil
		return nil
	}

	bytes, ok := value.([]byte)
	if !ok {
		return fmt.Errorf("failed to scan MetadataMap: %T", value)
	}

	return json.Unmarshal(bytes, m)
}

func (m MetadataMap) Value() (driver.Value, error) {
	if m == nil {
		return nil, nil
	}
	return json.Marshal(m)
}

func (sk *StoredKey) ToAPIKey() *apikey.APIKey {
	return &apikey.APIKey{
		ID:             sk.ID,
		Name:           sk.Name,
		Prefix:         sk.Prefix,
		OwnerID:        sk.OwnerID,
		OrganizationID: sk.OrganizationID,
		Scopes:         sk.Scopes,
		Description:    sk.Description,
		Environment:    sk.Environment,
		ExpiresAt:      sk.ExpiresAt,
		LastUsedAt:     sk.LastUsedAt,
		LastUsedIP:     sk.LastUsedIP,
		Revoked:        sk.Revoked,
		RevokedAt:      sk.RevokedAt,
		RevokedReason:  sk.RevokedReason,
		Metadata:       sk.Metadata,
		CreatedAt:      sk.CreatedAt,
		UpdatedAt:      sk.UpdatedAt,
	}
}
