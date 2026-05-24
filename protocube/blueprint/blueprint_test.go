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

func TestVolumeUnmarshalYAML_Shorthand(t *testing.T) {
	const yamlFour = `volumes:
  - world:worlds/world:/world:cow
`
	var st State
	if err := yaml.Unmarshal([]byte(yamlFour), &st); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if len(st.Volumes) != 1 {
		t.Fatalf("len volumes: got %d", len(st.Volumes))
	}
	v := st.Volumes[0]
	if v.Name != "world" || v.Source != "worlds/world" || v.Target != "/world" || v.Mode != VolumeModeCOW {
		t.Fatalf("unexpected volume: %+v", v)
	}

	const yamlThree = `volumes:
  - data:shared/data:/data
`
	if err := yaml.Unmarshal([]byte(yamlThree), &st); err != nil {
		t.Fatalf("unmarshal three-part: %v", err)
	}
	v = st.Volumes[0]
	if v.Name != "data" || v.Source != "shared/data" || v.Target != "/data" || v.Mode != VolumeModeCOW {
		t.Fatalf("unexpected three-part volume: %+v", v)
	}

	const yamlMap = `volumes:
  - name: "world"
    source: "worlds/world"
    target: "/world"
    mode: ro
`
	if err := yaml.Unmarshal([]byte(yamlMap), &st); err != nil {
		t.Fatalf("unmarshal mapping: %v", err)
	}
	v = st.Volumes[0]
	if v.Name != "world" || v.Source != "worlds/world" || v.Target != "/world" || v.Mode != VolumeModeRO {
		t.Fatalf("unexpected mapping volume: %+v", v)
	}
}
