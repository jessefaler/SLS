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

	sqlDB, err := db.DB()
	if err != nil {
		return errors.WithStack(err)
	}

	// Use a single connection for SQLite safety
	sqlDB.SetMaxOpenConns(1)

	// Set performance PRAGMAs
	if err := db.Exec("PRAGMA journal_mode = WAL;").Error; err != nil {
		return errors.Wrap(err, "failed to set journal_mode")
	}
	if err := db.Exec("PRAGMA synchronous = NORMAL;").Error; err != nil {
		return errors.Wrap(err, "failed to set synchronous mode")
	}
	if err := db.Exec("PRAGMA temp_store = MEMORY;").Error; err != nil {
		return errors.Wrap(err, "failed to set temp_store")
	}
	if err := db.Exec("PRAGMA mmap_size = 268435456;").Error; err != nil {
		return errors.Wrap(err, "failed to set mmap_size")
	}

	// Run migrations after DB is fully configured
	if err := migrations(); err != nil {
		return errors.Wrap(err, "database: migration failed")
	}

	return nil
}

func migrations() error {
	err := Instance().AutoMigrate(&models.ServerRecord{})
	if err != nil {
		return errors.Wrap(err, "database: failed to auto migrate server data")
	}
	err = Instance().AutoMigrate(&models.NodeState{})
	if err != nil {
		return errors.Wrap(err, "database: failed to auto migrate node state data")
	}
	err = Instance().AutoMigrate(&models.StoredKey{})
	if err != nil {
		return errors.Wrap(err, "database: failed to auto migrate api key data")
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
