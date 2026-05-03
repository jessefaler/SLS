package auth

import (
	"time"

	"emperror.dev/errors"
	"github.com/google/uuid"
	"github.com/grokify/coreforge/identity/apikey"
	"gorm.io/gorm"
	"protoxon.com/sls/protocube/internal/database"
	"protoxon.com/sls/protocube/models"
)

// repository handles storing and retrieving api keys from the gorm database

func StoreKey(key *apikey.APIKey, keyHash string) error {
	sk := models.StoredKey{
		ID:             key.ID,
		Name:           key.Name,
		Prefix:         key.Prefix,
		OwnerID:        key.OwnerID,
		OrganizationID: key.OrganizationID,
		Scopes:         key.Scopes,
		Description:    key.Description,
		Environment:    key.Environment,
		ExpiresAt:      key.ExpiresAt,
		LastUsedAt:     key.LastUsedAt,
		LastUsedIP:     key.LastUsedIP,
		Revoked:        key.Revoked,
		RevokedReason:  key.RevokedReason,
		Metadata:       key.Metadata,
		CreatedAt:      key.CreatedAt,
		UpdatedAt:      key.UpdatedAt,
		Hash:           keyHash,
	}
	if err := database.Instance().Create(sk).Error; err != nil {
		return errors.WithDetails(
			errors.Wrap(err, "failed to save API key to database"),
			"key_id", key.ID,
			"key_name", key.Name,
			"key_prefix", key.Prefix,
			"owner_id", key.OwnerID,
			"scope", key.Scopes,
		)
	}
	return nil
}

func GetByPrefix(prefix string) (*apikey.APIKey, string, error) {
	var sk models.StoredKey
	if err := database.Instance().Where("prefix = ?", prefix).First(&sk).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, "", errors.New("API key not found")
		}
		return nil, "", errors.Wrap(err, "failed to query API key")
	}
	return sk.ToAPIKey(), sk.Hash, nil
}

func GetByID(id uuid.UUID) (*apikey.APIKey, string, error) {
	var sk models.StoredKey
	if err := database.Instance().Where("id = ?", id).First(&sk).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, "", errors.New("API key not found")
		}
		return nil, "", errors.Wrap(err, "failed to query API key by ID")
	}
	return sk.ToAPIKey(), sk.Hash, nil
}

// Lists all keys by OwnerID
func ListByOwner(ownerID uuid.UUID) ([]*apikey.APIKey, error) {
	var keys []models.StoredKey
	if err := database.Instance().Where("owner_id = ?", ownerID).Find(&keys).Error; err != nil {
		return nil, errors.Wrap(err, "failed to list API keys by owner")
	}

	apiKeys := make([]*apikey.APIKey, 0, len(keys))
	for _, sk := range keys {
		apiKeys = append(apiKeys, sk.ToAPIKey())
	}

	return apiKeys, nil
}

// List all keys by OrganizationID
func ListByOrganization(orgID uuid.UUID) ([]*apikey.APIKey, error) {
	var keys []models.StoredKey
	if err := database.Instance().Where("organization_id = ?", orgID).Find(&keys).Error; err != nil {
		return nil, errors.Wrap(err, "failed to list API keys by organization")
	}

	apiKeys := make([]*apikey.APIKey, 0, len(keys))
	for _, sk := range keys {
		apiKeys = append(apiKeys, sk.ToAPIKey())
	}

	return apiKeys, nil
}

// Update an existing API key
func UpdateKey(key *apikey.APIKey) error {
	sk := models.StoredKey{
		Name:           key.Name,
		Prefix:         key.Prefix,
		OwnerID:        key.OwnerID,
		OrganizationID: key.OrganizationID,
		Scopes:         key.Scopes,
		Description:    key.Description,
		Environment:    key.Environment,
		ExpiresAt:      key.ExpiresAt,
		LastUsedAt:     key.LastUsedAt,
		LastUsedIP:     key.LastUsedIP,
		Revoked:        key.Revoked,
		RevokedAt:      key.RevokedAt,
		RevokedReason:  key.RevokedReason,
		Metadata:       key.Metadata,
		UpdatedAt:      time.Now(),
	}

	// Update only existing key by ID
	if err := database.Instance().Model(&models.StoredKey{}).
		Where("id = ?", key.ID).
		Updates(sk).Error; err != nil {
		return errors.Wrap(err, "failed to update API key")
	}

	return nil
}

// Delete an API key by ID
func DeleteKey(id uuid.UUID) error {
	if err := database.Instance().Where("id = ?", id).Delete(&models.StoredKey{}).Error; err != nil {
		return errors.Wrap(err, "failed to delete API key")
	}
	return nil
}

// Update LastUsedAt and LastUsedIP for an API key
func UpdateLastUsed(id uuid.UUID, ip string) error {
	now := time.Now()
	if err := database.Instance().Model(&models.StoredKey{}).Where("id = ?", id).
		Updates(map[string]interface{}{
			"last_used_at": now,
			"last_used_ip": ip,
		}).Error; err != nil {
		return errors.Wrap(err, "failed to update last used info")
	}
	return nil
}
