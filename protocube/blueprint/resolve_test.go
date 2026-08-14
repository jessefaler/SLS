package blueprint

import (
	"testing"

	"protoxon.com/sls/protocube/environment"
	"protoxon.com/sls/protocube/software"
)

func TestResolveMixinExtendsAndBlueprintIncludes(t *testing.T) {
	mem := int64(2048)
	cpu := int64(100)

	raw := &LoadResult{
		Mixins: []*Mixin{
			{
				Meta: MixinMeta{ID: "base"},
				Server: &Server{
					Software: "platform",
					Limits:   &environment.Limits{MemoryLimit: &mem},
				},
				State: &State{Env: map[string]string{"A": "1", "B": "1"}},
			},
			{
				Meta:    MixinMeta{ID: "child"},
				Extends: []string{"base"},
				Server: &Server{
					Version: "1.0.0",
					Limits:  &environment.Limits{CpuLimit: &cpu},
				},
				State: &State{Env: map[string]string{"B": "2"}},
			},
		},
		Blueprints: []*Blueprint{
			{
				Meta:     Meta{ID: "bp", Name: "BP", Type: "game"},
				Includes: []string{"child"},
				Server: &Server{
					Image: "java_21",
				},
				State: &State{Env: map[string]string{"C": "3"}},
			},
		},
	}

	reg := software.NewRegistry()
	reg.Register(&software.Software{
		Id:   "platform",
		Name: "platform",
		DockerImages: map[string]string{
			"java_21": "ghcr.io/example/java:21",
		},
		StopCommand:  "stop",
		Invocation:   "java -jar server.jar",
		OnlineSignal: "Done",
	})
	softwareRegistry = reg

	out := Resolve(raw)
	if len(out.Mixins) != 2 {
		t.Fatalf("mixins: got %d", len(out.Mixins))
	}
	if len(out.Blueprints) != 1 {
		t.Fatalf("blueprints: got %d", len(out.Blueprints))
	}

	bp := out.Blueprints[0]
	if bp.Server.Software != "platform" {
		t.Fatalf("software: got %q", bp.Server.Software)
	}
	if bp.Server.Version != "1.0.0" {
		t.Fatalf("version: got %q", bp.Server.Version)
	}
	if bp.Server.Image != "ghcr.io/example/java:21" {
		t.Fatalf("image: got %q", bp.Server.Image)
	}
	if bp.Server.Limits == nil || bp.Server.Limits.MemoryLimit == nil || *bp.Server.Limits.MemoryLimit != 2048 {
		t.Fatalf("memory limit not inherited: %+v", bp.Server.Limits)
	}
	if bp.Server.Limits.CpuLimit == nil || *bp.Server.Limits.CpuLimit != 100 {
		t.Fatalf("cpu limit not inherited: %+v", bp.Server.Limits)
	}
	if bp.State.Env["A"] != "1" || bp.State.Env["B"] != "2" || bp.State.Env["C"] != "3" {
		t.Fatalf("env merge: %+v", bp.State.Env)
	}
}

func TestResolveAnnotationsDeepMergeVsls(t *testing.T) {
	raw := &LoadResult{
		Mixins: []*Mixin{
			{
				Meta: MixinMeta{ID: "vsls-base"},
				Server: &Server{
					Software: "platform",
					Version:  "1.0.0",
					Image:    "java_21",
				},
				Annotations: map[string]interface{}{
					"vsls": map[string]interface{}{
						"dont-stop-when-empty": true,
						"max-instances":        4,
						"on-join": []interface{}{
							map[string]interface{}{"run": "say from mixin"},
						},
					},
					"maintainer": "mixin",
				},
			},
		},
		Blueprints: []*Blueprint{
			{
				Meta:     Meta{ID: "bp", Name: "BP", Type: "game"},
				Includes: []string{"vsls-base"},
				Annotations: map[string]interface{}{
					"vsls": map[string]interface{}{
						"max-instances": 1,
						"matchmaking": map[string]interface{}{
							"maxPlayers": 8,
						},
					},
					"maintainer": "blueprint",
				},
			},
		},
	}

	reg := software.NewRegistry()
	reg.Register(&software.Software{
		Id:   "platform",
		Name: "platform",
		DockerImages: map[string]string{
			"java_21": "ghcr.io/example/java:21",
		},
		StopCommand:  "stop",
		Invocation:   "java -jar server.jar",
		OnlineSignal: "Done",
	})
	softwareRegistry = reg

	out := Resolve(raw)
	if len(out.Blueprints) != 1 {
		t.Fatalf("blueprints: got %d", len(out.Blueprints))
	}
	vsls, ok := annotationMap(out.Blueprints[0].Annotations["vsls"])
	if !ok {
		t.Fatalf("expected merged vsls map, got %#v", out.Blueprints[0].Annotations["vsls"])
	}
	if vsls["dont-stop-when-empty"] != true {
		t.Fatalf("mixin vsls.dont-stop-when-empty dropped: %#v", vsls)
	}
	if vsls["max-instances"] != 1 {
		t.Fatalf("blueprint vsls.max-instances should win: %#v", vsls["max-instances"])
	}
	mm, ok := annotationMap(vsls["matchmaking"])
	if !ok || mm["maxPlayers"] != 8 {
		t.Fatalf("blueprint vsls.matchmaking not merged: %#v", vsls["matchmaking"])
	}
	onJoin, ok := vsls["on-join"].([]interface{})
	if !ok || len(onJoin) != 1 {
		t.Fatalf("mixin vsls.on-join dropped: %#v", vsls["on-join"])
	}
	if out.Blueprints[0].Annotations["maintainer"] != "blueprint" {
		t.Fatalf("scalar annotation should be replaced: %#v", out.Blueprints[0].Annotations["maintainer"])
	}
}

func TestMergeAnnotationsNestedDoesNotMutateInputs(t *testing.T) {
	base := map[string]interface{}{
		"vsls": map[string]interface{}{"max-instances": 4},
	}
	overlay := map[string]interface{}{
		"vsls": map[string]interface{}{"dont-stop-when-empty": true},
	}
	got := mergeAnnotations(base, overlay)
	vsls, ok := annotationMap(got["vsls"])
	if !ok || vsls["max-instances"] != 4 || vsls["dont-stop-when-empty"] != true {
		t.Fatalf("merge: %#v", got)
	}
	baseVsls := base["vsls"].(map[string]interface{})
	if _, exists := baseVsls["dont-stop-when-empty"]; exists {
		t.Fatalf("merge mutated base: %#v", base)
	}
	overlayVsls := overlay["vsls"].(map[string]interface{})
	if _, exists := overlayVsls["max-instances"]; exists {
		t.Fatalf("merge mutated overlay: %#v", overlay)
	}
}

func TestResolveMixinCycle(t *testing.T) {
	raw := &LoadResult{
		Mixins: []*Mixin{
			{Meta: MixinMeta{ID: "mixin_configs"}, Extends: []string{"mixin_plugins"}},
			{Meta: MixinMeta{ID: "mixin_plugins"}, Extends: []string{"mixin_configs"}},
		},
	}
	out := Resolve(raw)
	if len(out.Mixins) != 0 {
		t.Fatalf("expected cycle to omit mixins, got %d", len(out.Mixins))
	}
}

func TestFormatMixinCycle(t *testing.T) {
	got := formatMixinCycle([]string{"mixin_configs", "mixin_plugins"}, "mixin_configs")
	want := "mixin_configs -> mixin_plugins -> mixin_configs"
	if got != want {
		t.Fatalf("got %q, want %q", got, want)
	}

	got = formatMixinCycle([]string{"a", "b", "c"}, "b")
	want = "b -> c -> b"
	if got != want {
		t.Fatalf("got %q, want %q", got, want)
	}
}

func TestClassifyDocument(t *testing.T) {
	tests := []struct {
		name string
		yaml string
		want documentKind
	}{
		{name: "blueprint", yaml: "blueprint:\n  id: a\n", want: documentBlueprint},
		{name: "mixin", yaml: "mixin:\n  id: b\n", want: documentMixin},
		{name: "ambiguous", yaml: "blueprint:\n  id: a\nmixin:\n  id: b\n", want: documentAmbiguous},
		{name: "unknown", yaml: "server:\n  software: x\n", want: documentUnknown},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := classifyDocument([]byte(tt.yaml))
			if err != nil {
				t.Fatalf("classifyDocument: %v", err)
			}
			if got != tt.want {
				t.Fatalf("got %v, want %v", got, tt.want)
			}
		})
	}
}
