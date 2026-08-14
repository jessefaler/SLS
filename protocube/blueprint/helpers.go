package blueprint

import (
	"maps"

	"protoxon.com/sls/protocube/environment"
)

// MergeState merges the provided environment variables into the base state's
// environment variables (env wins on key collision). Used for per-server overrides.
func MergeState(base *State, env map[string]string) *State {
	if len(env) == 0 {
		return base
	}
	out := copyState(base)
	if out == nil {
		out = &State{}
	}
	if out.Env == nil {
		out.Env = make(map[string]string, len(env))
	} else {
		out.Env = maps.Clone(out.Env)
	}
	for k, v := range env {
		out.Env[k] = v
	}
	return out
}

// mergeMixinOverlay merges overlay onto base. Overlay wins on conflicts.
// Meta and Extends are left to the caller.
func mergeMixinOverlay(base, overlay *Mixin) *Mixin {
	if overlay == nil {
		return copyMixin(base)
	}
	if base == nil {
		return &Mixin{
			Server:      copyServer(overlay.Server),
			State:       copyState(overlay.State),
			Annotations: cloneAnnotations(overlay.Annotations),
		}
	}
	return &Mixin{
		Server:      mergeServer(base.Server, overlay.Server),
		State:       mergeStates(base.State, overlay.State),
		Annotations: mergeAnnotations(base.Annotations, overlay.Annotations),
	}
}

func mergeServer(base, overlay *Server) *Server {
	if overlay == nil {
		return copyServer(base)
	}
	if base == nil {
		return copyServer(overlay)
	}

	out := copyServer(base)
	if overlay.Software != "" {
		out.Software = overlay.Software
	}
	if overlay.Version != "" {
		out.Version = overlay.Version
	}
	if overlay.Image != "" {
		out.Image = overlay.Image
	}
	if overlay.Path != "" {
		out.Path = overlay.Path
	}
	out.Limits = environment.MergeLimits(environment.CopyLimits(out.Limits), overlay.Limits)
	out.Configs = mergeConfigs(out.Configs, overlay.Configs)
	return out
}

func mergeStates(base, overlay *State) *State {
	if overlay == nil {
		return copyState(base)
	}
	if base == nil {
		return copyState(overlay)
	}

	out := copyState(base)

	if len(overlay.Volumes) > 0 {
		byName := make(map[string]int, len(out.Volumes))
		for i, v := range out.Volumes {
			byName[v.Name] = i
		}
		for _, v := range overlay.Volumes {
			if i, ok := byName[v.Name]; ok {
				out.Volumes[i] = v
			} else {
				byName[v.Name] = len(out.Volumes)
				out.Volumes = append(out.Volumes, v)
			}
		}
	}

	if len(overlay.Mounts) > 0 {
		out.Mounts = append(out.Mounts, overlay.Mounts...)
	}
	if len(overlay.Copy) > 0 {
		out.Copy = append(out.Copy, overlay.Copy...)
	}
	if len(overlay.Env) > 0 {
		if out.Env == nil {
			out.Env = maps.Clone(overlay.Env)
		} else {
			maps.Copy(out.Env, overlay.Env)
		}
	}

	return out
}

// mergeAnnotations deep-merges annotation maps. Nested maps are merged
// recursively; overlay wins on scalars, lists, and type mismatches.
// This keeps mixin annotations.vsls keys when a blueprint only sets some of them.
func mergeAnnotations(base, overlay map[string]interface{}) map[string]interface{} {
	if len(overlay) == 0 {
		return cloneAnnotations(base)
	}
	if len(base) == 0 {
		return cloneAnnotations(overlay)
	}
	out := cloneAnnotations(base)
	for k, ov := range overlay {
		bv, ok := out[k]
		if !ok {
			out[k] = cloneAnnotationValue(ov)
			continue
		}
		baseMap, baseIsMap := annotationMap(bv)
		overMap, overIsMap := annotationMap(ov)
		if baseIsMap && overIsMap {
			out[k] = mergeAnnotations(baseMap, overMap)
			continue
		}
		out[k] = cloneAnnotationValue(ov)
	}
	return out
}

func cloneAnnotations(in map[string]interface{}) map[string]interface{} {
	if len(in) == 0 {
		return nil
	}
	out := make(map[string]interface{}, len(in))
	for k, v := range in {
		out[k] = cloneAnnotationValue(v)
	}
	return out
}

func cloneAnnotationValue(v interface{}) interface{} {
	if m, ok := annotationMap(v); ok {
		return cloneAnnotations(m)
	}
	if s, ok := v.([]interface{}); ok {
		out := make([]interface{}, len(s))
		for i, item := range s {
			out[i] = cloneAnnotationValue(item)
		}
		return out
	}
	return v
}

func annotationMap(v interface{}) (map[string]interface{}, bool) {
	switch m := v.(type) {
	case map[string]interface{}:
		return m, true
	case map[interface{}]interface{}:
		out := make(map[string]interface{}, len(m))
		for k, val := range m {
			ks, ok := k.(string)
			if !ok {
				return nil, false
			}
			out[ks] = val
		}
		return out, true
	default:
		return nil, false
	}
}

func mergeConfigs(base, overlay map[string]ConfigFile) map[string]ConfigFile {
	if len(overlay) == 0 {
		return cloneConfigs(base)
	}
	if len(base) == 0 {
		return cloneConfigs(overlay)
	}
	out := cloneConfigs(base)
	for k, v := range overlay {
		out[k] = cloneConfigFile(v)
	}
	return out
}

func copyMixin(m *Mixin) *Mixin {
	if m == nil {
		return nil
	}
	return &Mixin{
		Meta:        m.Meta,
		Extends:     append([]string(nil), m.Extends...),
		Server:      copyServer(m.Server),
		State:       copyState(m.State),
		Annotations: cloneAnnotations(m.Annotations),
	}
}

func copyServer(s *Server) *Server {
	if s == nil {
		return nil
	}
	return &Server{
		Software: s.Software,
		Version:  s.Version,
		Image:    s.Image,
		Path:     s.Path,
		Limits:   environment.CopyLimits(s.Limits),
		Configs:  cloneConfigs(s.Configs),
	}
}

func copyState(s *State) *State {
	if s == nil {
		return nil
	}
	out := &State{
		Env: maps.Clone(s.Env),
	}
	if len(s.Volumes) > 0 {
		out.Volumes = append([]Volume(nil), s.Volumes...)
	}
	if len(s.Mounts) > 0 {
		out.Mounts = append([]Mount(nil), s.Mounts...)
	}
	if len(s.Copy) > 0 {
		out.Copy = append([]Copy(nil), s.Copy...)
	}
	return out
}

func cloneConfigs(in map[string]ConfigFile) map[string]ConfigFile {
	if len(in) == 0 {
		return nil
	}
	out := make(map[string]ConfigFile, len(in))
	for k, v := range in {
		out[k] = cloneConfigFile(v)
	}
	return out
}

func cloneConfigFile(c ConfigFile) ConfigFile {
	return ConfigFile{
		Parser: c.Parser,
		Find:   maps.Clone(c.Find),
	}
}
