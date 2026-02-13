package repository

import (
	"emperror.dev/errors"
	"protoxon.com/sls/protocube/internal/database"
	"protoxon.com/sls/protocube/models"
)

func StoreServer(server *models.ServerStore) error {
	if err := database.Instance().Create(server).Error; err != nil {
		return errors.Wrap(err, "failed to save server to database")
	}
	return nil
}

func RemoveServer(id string) error {
	if err := database.Instance().Where("id = ?", id).Delete(&models.ServerStore{}).Error; err != nil {
		return errors.Wrap(err, "failed to remove server from database")
	}
	return nil
}

// GetAllServers returns all servers stored in the database
func GetAllServers() ([]*models.ServerStore, error) {
	servers := make([]*models.ServerStore, 0)
	if err := database.Instance().Find(&servers).Error; err != nil {
		return nil, errors.Wrap(err, "failed to fetch servers from database")
	}
	return servers, nil
}
