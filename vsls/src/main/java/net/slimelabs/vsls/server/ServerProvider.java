package net.slimelabs.vsls.server;

import java.util.Optional;

public interface ServerProvider {
    Server getServer(String id);
    Optional<Server> getOrFetch(String id);
}
