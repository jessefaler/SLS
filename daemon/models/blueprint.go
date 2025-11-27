package models

type Content struct {
	Name   string `yaml:"name" json:"name"`
	Source string `yaml:"source" json:"source"` // The source path relative to the root content folder
}
