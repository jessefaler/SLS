package blueprint

import (
	"fmt"
	"testing"

	"github.com/apex/log"
	"gopkg.in/yaml.v3"
)

func TestBlueprint(t *testing.T) {

	blueprint, err := load("./example.yml")
	if err != nil {
		log.WithField("file", "./example.yml").Warnf("Failed to load blueprint: %v", err)
	}

	marshal, err := yaml.Marshal(blueprint)
	if err != nil {
		return
	}

	fmt.Println(string(marshal))

}
