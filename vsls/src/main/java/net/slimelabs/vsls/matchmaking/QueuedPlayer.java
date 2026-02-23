package net.slimelabs.vsls.matchmaking;

import com.velocitypowered.api.proxy.Player;

public record QueuedPlayer(Player player, String preferredBlueprintId) {}
