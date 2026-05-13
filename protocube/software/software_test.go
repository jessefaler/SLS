package software

import (
	"fmt"
	"testing"

	"github.com/apex/log"
	"gopkg.in/yaml.v3"
)

func TestSoftware(t *testing.T) {

	software, err := load("./example.yml")
	if err != nil {
		log.WithField("file", "./example.yml").Warnf("Failed to load software: %v", err)
	}

	marshal, err := yaml.Marshal(software)
	if err != nil {
		return
	}

	fmt.Println(string(marshal))

}
