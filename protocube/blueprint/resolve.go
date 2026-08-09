package blueprint

import (
	"strings"

	"emperror.dev/errors"
	"github.com/apex/log"
)

// Resolve flattens mixin extends chains, applies blueprint includes, then runs
// completeness validation on each composed blueprint. Failed items are logged
// and omitted from the result.
func Resolve(raw *LoadResult) *LoadResult {
	if raw == nil {
		return &LoadResult{}
	}

	rawByID := make(map[string]*Mixin, len(raw.Mixins))
	for _, m := range raw.Mixins {
		rawByID[m.Meta.ID] = m
	}

	resolvedByID := make(map[string]*Mixin, len(raw.Mixins))
	visiting := make(map[string]bool)
	var stack []string

	var resolveMixin func(id string) (*Mixin, error)
	resolveMixin = func(id string) (*Mixin, error) {
		if cached, ok := resolvedByID[id]; ok {
			return cached, nil
		}
		if visiting[id] {
			return nil, errors.Errorf("mixin inheritance cycle detected: %s", formatMixinCycle(stack, id))
		}
		rawMixin, ok := rawByID[id]
		if !ok {
			return nil, errors.Errorf("unknown mixin %q", id)
		}

		visiting[id] = true
		stack = append(stack, id)
		defer func() {
			stack = stack[:len(stack)-1]
			delete(visiting, id)
		}()

		var acc *Mixin
		for _, parentID := range rawMixin.Extends {
			parent, err := resolveMixin(parentID)
			if err != nil {
				return nil, err
			}
			acc = mergeMixinOverlay(acc, parent)
		}

		self := &Mixin{
			Server:      rawMixin.Server,
			State:       rawMixin.State,
			Annotations: rawMixin.Annotations,
		}
		acc = mergeMixinOverlay(acc, self)
		if acc == nil {
			acc = &Mixin{}
		}
		acc.Meta = rawMixin.Meta
		acc.Extends = append([]string(nil), rawMixin.Extends...)

		resolvedByID[id] = acc
		return acc, nil
	}

	out := &LoadResult{}

	for _, m := range raw.Mixins {
		resolved, err := resolveMixin(m.Meta.ID)
		if err != nil {
			log.WithField("mixin", m.Meta.ID).Errorf("Failed to resolve mixin: %v", err)
			continue
		}
		out.Mixins = append(out.Mixins, resolved)
	}

	for _, bp := range raw.Blueprints {
		resolved, err := resolveBlueprint(bp, resolvedByID)
		if err != nil {
			log.WithField("blueprint", bp.Meta.ID).Errorf("Failed to resolve blueprint: %v", err)
			continue
		}
		out.Blueprints = append(out.Blueprints, resolved)
	}

	return out
}

// formatMixinCycle returns the cycle path, e.g. "a -> b -> a".
func formatMixinCycle(stack []string, id string) string {
	start := 0
	for i, s := range stack {
		if s == id {
			start = i
			break
		}
	}
	parts := make([]string, 0, len(stack)-start+1)
	parts = append(parts, stack[start:]...)
	parts = append(parts, id)
	return strings.Join(parts, " -> ")
}

func resolveBlueprint(bp *Blueprint, mixins map[string]*Mixin) (*Blueprint, error) {
	out := &Blueprint{
		Meta:     bp.Meta,
		Includes: append([]string(nil), bp.Includes...),
		Save:     bp.Save,
	}

	var server *Server
	var state *State
	var annotations map[string]interface{}

	for _, id := range bp.Includes {
		m, ok := mixins[id]
		if !ok {
			return nil, errors.Errorf("includes unknown mixin %q", id)
		}
		server = mergeServer(server, m.Server)
		state = mergeStates(state, m.State)
		annotations = mergeAnnotations(annotations, m.Annotations)
	}

	server = mergeServer(server, bp.Server)
	state = mergeStates(state, bp.State)
	annotations = mergeAnnotations(annotations, bp.Annotations)

	if server == nil {
		return nil, errors.New("missing required section: server")
	}
	if err := server.Validate(); err != nil {
		return nil, errors.WithMessage(err, "server")
	}
	if state != nil {
		if err := state.Validate(); err != nil {
			return nil, err
		}
	}

	out.Server = server
	out.State = state
	out.Annotations = annotations
	return out, nil
}
