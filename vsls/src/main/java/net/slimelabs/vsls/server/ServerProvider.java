package net.slimelabs.vsls.server;

import com.protoxon.S4J.client.entities.ClientServer;
import com.protoxon.S4J.exceptions.NotFoundException;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;

import java.util.Optional;

public interface ServerProvider {
    Server getServer(String id);
    Optional<Server> getOrFetch(String id);
}
