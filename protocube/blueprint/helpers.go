package blueprint

import "maps"

// MergeState merges the provided environment variables into the base states environment variables
// (env wins on key collision)
func MergeState(base *State, env map[string]string) *State {
	if len(env) == 0 {
		return base
	}
	out := &State{}
	if base != nil {
		out.Volumes = base.Volumes
		out.Mounts = base.Mounts
		out.Copy = base.Copy
		if len(base.Env) > 0 {
			out.Env = maps.Clone(base.Env)
		}
	}
	for k, v := range env {
		if out.Env == nil {
			out.Env = make(map[string]string, len(env))
		}
		out.Env[k] = v
	}
	return out
}
