package net.slimelabs.sls.utils;

import com.velocitypowered.api.proxy.Player;
import net.slimelabs.sls.SLS;

import java.util.Optional;
import java.util.UUID;

public class PlayerUtils {
    public static Optional<UUID> getPlayerUUID(String playerName) {
        Optional<Player> playerOptional = SLS.PROXY.getPlayer(playerName);
        return playerOptional.map(Player::getUniqueId);
    }
}
