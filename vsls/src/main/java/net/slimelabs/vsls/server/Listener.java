package net.slimelabs.vsls.server;


import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entites.ServerCrashEvent;

import java.util.ArrayList;
import java.util.List;

public class Listener {

    private final List<StatusChangeListener> statusListeners = new ArrayList<>();
    private final List<Listener.CrashListener> crashListeners = new ArrayList<>();

    @FunctionalInterface
    public interface StatusChangeListener {
        void onStatusChange(ServerStatus status);
    }

    @FunctionalInterface
    public interface CrashListener {
        void onCrash(ServerCrashEvent crash);
    }

    public void onStatusChange(Listener.StatusChangeListener listener) {
        statusListeners.add(listener);
    }

    public void onCrash(Listener.CrashListener listener) {
        crashListeners.add(listener);
    }

    protected void fireStatusChange(ServerStatus status) {
        for (StatusChangeListener l : statusListeners) {
            l.onStatusChange(status);
        }
    }

    protected void fireCrash(ServerCrashEvent crash) {
        for (CrashListener l : crashListeners) {
            l.onCrash(crash);
        }
    }

}

