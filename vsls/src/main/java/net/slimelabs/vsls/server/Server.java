package net.slimelabs.vsls.server;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.client.entites.ServerCrashEvent;
import com.protoxon.S4J.client.entites.ServerDeletedEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class Server {

    public String id;
    // The servers api client
    public ClientServer client;
    // Runnable that removes this server from the registry when executed
    private final Runnable unregister;
    // Servers last updated status
    public ServerStatus status = ServerStatus.UNKNOWN;

    public Server(ClientServer client, Runnable unregister) {
        this.client = client;
        this.id = client.getId();
        this.unregister = unregister;
    }

    public String getId() {
        return id;
    }

    public SLSAction<Void> stop() {
        SLSAction<Void> action = client.stop();
        return action.map(v -> {
            unregister.run();
            return v;
        });
    }

    public SLSAction<Void> kill() {
        SLSAction<Void> action = client.kill();
        return action.map(v -> {
            unregister.run();
            return v;
        });
    }

    public void onStatusChange(ServerStatus status) {
        Log.info("Server " + id + " changed status to " + status);
        Player source = SLS.proxy.getPlayer("protoxon").get();
        ProtoMessage.chat()
                .add("Server ", NamedTextColor.GRAY)
                .add("(", NamedTextColor.DARK_GRAY)
                .add(id, NamedTextColor.RED)
                .add(")", NamedTextColor.DARK_GRAY)
                .add(" changed status to: ", NamedTextColor.GRAY)
                .add(status.getStatus(), NamedTextColor.RED)
                .sendMessage(source);
    }

    public void onCrash(ServerCrashEvent crashEvent) {
    }

    public void onUnregistration() {
        Player source = SLS.proxy.getPlayer("protoxon").get();
        ProtoMessage.chat()
                .add("Server ", NamedTextColor.GRAY)
                .add("(", NamedTextColor.DARK_GRAY)
                .add(id, NamedTextColor.RED)
                .add(")", NamedTextColor.DARK_GRAY)
                .add(" unregistered ", NamedTextColor.GRAY)
                .sendMessage(source);
    }

    public void onDeletion() {

    }

    public SLSAction<ServerStatus> getRemoteStatus() {
        return client.getStatus();
    }

}
