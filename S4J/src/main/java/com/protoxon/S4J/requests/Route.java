package com.protoxon.S4J.requests;

import com.protoxon.S4J.utils.Checks;

import static com.protoxon.S4J.requests.Method.*;

public class Route {

    // ===========================================================
    // API Routes
    // ===========================================================

    public static class Servers {

        public static final Route CREATE_SERVER = new Route(POST, "servers");
        public static final Route GET_ALL_SERVERS = new Route(GET, "servers");

    }

    public static class Server {

        public static final Route GET_SERVER = new Route(GET, "servers/{server_id}");
        public static final Route SET_POWER = new Route(POST, "servers/{server_id}/power");
        public static final Route STATUS = new Route(GET, "servers/{server_id}/status");
        public static final Route STATS = new Route(GET, "servers/{server_id}/stats");
        public static final Route COMMANDS = new Route(POST, "servers/{server_id}/commands");
        public static final Route LOGS = new Route(GET, "servers/{server_id}/logs");
        public static final Route INSTALL = new Route(GET, "servers/{server_id}/install");
        public static final Route INSTALL_LOGS = new Route(GET, "servers/{server_id}/install/logs");
        public static final Route REINSTALL = new Route(POST, "servers/{server_id}/reinstall");
        public static final Route DELETE = new Route(Method.DELETE, "servers/{server_id}");
        public static final Route RESET = new Route(POST, "servers/{server_id}/reset");

    }

    public static class Events {

        public static final Route EVENT_STREAM = new Route(GET, "events");
        public static final Route WEBSOCKET_EVENTS = new Route(GET, "events/ws");

    }

    public static class Blueprints {
        public static final Route GET_BLUEPRINTS = new Route(GET, "blueprints");
        public static final Route RELOAD = new Route(POST, "blueprints/reload");
    }

    public static class Blueprint {
        public static final Route GET_BLUEPRINT = new Route(GET, "blueprints/{blueprint_id}");
    }

    public static class Mixins {
        public static final Route GET_MIXINS = new Route(GET, "mixins");
    }

    public static class Mixin {
        public static final Route GET_MIXIN = new Route(GET, "mixins/{mixin_id}");
    }

    public static class Software {
        public static final Route RELOAD = new Route(POST, "software/reload");
    }

    public static class System {
        public static final Route GET_SYSTEM_INFORMATION = new Route(GET, "system");
    }

    public static class Nodes {
        public static final Route GET_ALL_NODES = new Route(GET, "nodes");
    }

    public static class Node {
        public static final Route GET_NODE = new Route(GET, "nodes/{node_id}");
        public static final Route GET_SYSTEM_INFO = new Route(GET, "nodes/{node_id}/system");
        public static final Route SET_DRAINED = new Route(PATCH, "nodes/{node_id}/drained");
    }

    // ===========================================================
    // Routing Utility Logic
    // ===========================================================

    private final Method method;
    private final String route;
    private final String compilableRoute;
    private final int paramCount;

    private Route(Method method, String route) {
        this.method = method;
        this.route = route;
        this.paramCount = countMatches(route, '{');

        compilableRoute = route.replaceAll("\\{.*?\\}", "%s");

        if (paramCount != countMatches(route, '}'))
            throw new IllegalArgumentException(
                    "An argument does not have both {}'s for route: " + method + "  " + route);
    }

    public String getRoute() {
        return route;
    }

    @Override
    public String toString() {
        return "Route(" + method + ": " + route + ")";
    }

    public CompiledRoute compile(String... params) {
        if (params.length != paramCount)
            throw new IllegalArgumentException(
                    "Error Compiling Route: [" + route + "], incorrect amount of parameters provided. " + "Expected: "
                            + paramCount + ", Provided: " + params.length);

        if (paramCount == 0) return new CompiledRoute(this, compilableRoute);

        String compiledRoute = String.format(compilableRoute, (Object[]) params);

        return new CompiledRoute(this, compiledRoute);
    }

    public static class CompiledRoute {
        private final Route baseRoute;
        private final String compiledRoute;

        private CompiledRoute(Route baseRoute, String compiledRoute) {
            this.baseRoute = baseRoute;
            this.compiledRoute = compiledRoute;
        }

        public String getCompiledRoute() {
            return compiledRoute;
        }

        public Route getBaseRoute() {
            return baseRoute;
        }

        public Method getMethod() {
            return baseRoute.method;
        }

        public CompiledRoute withQueryParams(String... params) {
            Checks.check(params.length >= 2, "Params length must be at least 2");
            Checks.check(params.length % 2 == 0, "Params length must be a multiple of 2");

            boolean hasQueryParams = compiledRoute.contains("?");

            StringBuilder newRoute = new StringBuilder(compiledRoute);
            for (int i = 0; i < params.length; i++)
                newRoute.append(!hasQueryParams && i == 0 ? '?' : '&')
                        .append(params[i])
                        .append('=')
                        .append(params[++i]);

            return new CompiledRoute(baseRoute, newRoute.toString());
        }
    }

    private static int countMatches(CharSequence seq, char c) {
        int count = 0;
        for (int i = 0; i < seq.length(); i++) {
            if (seq.charAt(i) == c) count++;
        }
        return count;
    }


}
