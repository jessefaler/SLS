package blueprint

import (
	"fmt"
	"testing"

	"gopkg.in/yaml.v3"
	"protoxon.com/sls/protocube/software"
)

func TestBlueprint(t *testing.T) {
	reg := software.NewRegistry()
	reg.Register(&software.Software{
		Id:   "platform",
		Name: "Platform",
		DockerImages: map[string]string{
			"ghcr.io/protoxon/images:java_21": "ghcr.io/protoxon/images:java_21",
		},
		StopCommand:  "stop",
		Invocation:   "java -jar server.jar",
		OnlineSignal: "Done",
	})
	softwareRegistry = reg

	blueprint, err := load("./example.yml")
	if err != nil {
		t.Fatalf("load example blueprint: %v", err)
	}

	marshal, err := yaml.Marshal(blueprint)
	if err != nil {
		t.Fatalf("marshal blueprint: %v", err)
	}

	fmt.Println(string(marshal))
}
