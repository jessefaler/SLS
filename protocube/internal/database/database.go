package database

import (
	"path/filepath"

	"emperror.dev/errors"
	"github.com/glebarez/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/models"
	"protoxon.com/sls/protocube/system"
)

var (
	o  system.AtomicBool
	db *gorm.DB
)

// Initialize configures the local SQLite database for Protocube.
func Initialize() error {
	if !o.SwapIf(true) {
		panic("database: attempt to initialize more than once during application lifecycle")
	}
	p := filepath.Join(config.Get().System.RootDirectory, "protocube.db")
	instance, err := gorm.Open(sqlite.Open(p), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	if err != nil {
		return errors.Wrap(err, "database: could not open database file")
	}
	db = instance
	if sql, err := db.DB(); err != nil {
		return errors.WithStack(err)
	} else {
		sql.SetMaxOpenConns(1)
	}
	if err := migrations(); err != nil {
		return errors.Wrap(err, "database: migration failed")
	}
	return nil
}

func migrations() error {
	err := Instance().AutoMigrate(&models.ServerStore{})
	if err != nil {
		return errors.Wrap(err, "database: failed to auto migrate server data")
	}
	err = Instance().AutoMigrate(&models.NodeState{})
	if err != nil {
		return errors.Wrap(err, "database: failed to auto migrate node state data")
	}
	return nil
}

// Instance returns the gorm database instance that was configured when the application was
// booted.
func Instance() *gorm.DB {
	if db == nil {
		panic("database: attempt to access instance before initialized")
	}
	return db
}
