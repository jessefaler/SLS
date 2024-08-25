package net.slimelabs.sls.registries;

import net.slimelabs.sls.SLS;

public class RegistryRouter {

    public boolean containsWorld(String worldName) {
        return SLS.MINIGAME_REGISTRY.containsMinigame(worldName) || SLS.ARCHIVE_REGISTRY.containsArchiveMap(worldName) || SLS.ADVENTURE_REGISTRY.containsAdventure(worldName);
    }

    public String getFolderName(String worldName) {
        if(SLS.MINIGAME_REGISTRY.containsMinigame(worldName)) {
            return SLS.MINIGAME_REGISTRY.getFolderName(worldName);
        }
        if(SLS.ADVENTURE_REGISTRY.containsAdventure(worldName)) {
            return SLS.ADVENTURE_REGISTRY.getFolderName(worldName);
        }
        if(SLS.ARCHIVE_REGISTRY.containsArchiveMap(worldName)) {
            return SLS.ARCHIVE_REGISTRY.getFolderName(worldName);
        }
        return null;
    }

    public int getRAM(String worldName) {
        if(SLS.MINIGAME_REGISTRY.containsMinigame(worldName)) {
            return SLS.MINIGAME_REGISTRY.getCustomRam(worldName);
        }
        if(SLS.ADVENTURE_REGISTRY.containsAdventure(worldName)) {
            return SLS.ADVENTURE_REGISTRY.getCustomRam(worldName);
        }
        if(SLS.ARCHIVE_REGISTRY.containsArchiveMap(worldName)) {
            return SLS.ARCHIVE_REGISTRY.getCustomRam(worldName);
        }
        return -1;
    }

    public boolean getUseCustomJDK(String worldName) {
        if(SLS.MINIGAME_REGISTRY.containsMinigame(worldName)) {
            return SLS.MINIGAME_REGISTRY.getUseCustomJDK(worldName);
        }
        if(SLS.ADVENTURE_REGISTRY.containsAdventure(worldName)) {
            return SLS.ADVENTURE_REGISTRY.getUseCustomJDK(worldName);
        }
        if(SLS.ARCHIVE_REGISTRY.containsArchiveMap(worldName)) {
            return SLS.ARCHIVE_REGISTRY.getUseCustomJDK(worldName);
        }
        return false;
    }

    public String getCustomJDKPath(String worldName) {
        if(SLS.MINIGAME_REGISTRY.containsMinigame(worldName)) {
            return SLS.MINIGAME_REGISTRY.getCustomJDKPath(worldName);
        }
        if(SLS.ADVENTURE_REGISTRY.containsAdventure(worldName)) {
            return SLS.ADVENTURE_REGISTRY.getCustomJDKPath(worldName);
        }
        if(SLS.ARCHIVE_REGISTRY.containsArchiveMap(worldName)) {
            return SLS.ARCHIVE_REGISTRY.getCustomJDKPath(worldName);
        }
        return null;
    }

    public boolean getReset(String worldName) {
        if(SLS.MINIGAME_REGISTRY.containsMinigame(worldName)) {
            return SLS.MINIGAME_REGISTRY.getReset(worldName);
        }
        if(SLS.ADVENTURE_REGISTRY.containsAdventure(worldName)) {
            return SLS.ADVENTURE_REGISTRY.getReset(worldName);
        }
        if(SLS.ARCHIVE_REGISTRY.containsArchiveMap(worldName)) {
            return SLS.ARCHIVE_REGISTRY.getReset(worldName);
        }
        return false;
    }


}
