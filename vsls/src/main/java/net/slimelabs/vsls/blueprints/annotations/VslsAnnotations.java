package net.slimelabs.vsls.blueprints.annotations;

import com.protoxon.S4J.entities.Blueprint;

import java.util.Map;

/**
 * Reads optional vSLS settings from blueprint annotations.
 *
 * Expected shape:
 * annotations:
 *   vsls:
 *     dont-stop-when-empty: true|false
 *     max-instances: <int>
 */
public final class VslsAnnotations {

    private VslsAnnotations() {}

    public static boolean dontStopWhenEmpty(Blueprint blueprint) {
        Map<String, Object> vsls = getVslsMap(blueprint);
        if (vsls == null) return false; // default
        Object raw = vsls.get("dont-stop-when-empty");
        return raw instanceof Boolean b && b;
    }

    /**
     * @return max instances for this blueprint, or {@link Integer#MAX_VALUE} if unset/invalid.
     */
    public static int maxInstances(Blueprint blueprint) {
        Map<String, Object> vsls = getVslsMap(blueprint);
        if (vsls == null) return Integer.MAX_VALUE;
        Object raw = vsls.get("max-instances");
        int value = toInt(raw);
        return value > 0 ? value : Integer.MAX_VALUE;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getVslsMap(Blueprint blueprint) {
        if (blueprint == null) return null;
        Object annotations = blueprint.getAnnotations();
        if (!(annotations instanceof Map<?, ?> root)) return null;
        Object vslsObj = root.get("vsls");
        if (!(vslsObj instanceof Map<?, ?> vslsMap)) return null;
        return (Map<String, Object>) vslsMap;
    }

    private static int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        return 0;
    }
}

