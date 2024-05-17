# SLN
Server managment plugin for SlimeLabs.net

Update 2.0.1 - 12/1/2023 9:43 AM -->

Changes from V-2.0.0:

1. Minigame names are now read from the config in lowercase for consistency.
2. Casing checks for minigame names have been removed.
3. The commands class has been rewritten for enhanced modularity. Adding new commands is now much easier, and commands include usage messages.
4. Added permission "sln.command.admin" to the /sln command. Players can still use /sln join <minigame> without the permission but can't force other players to join.
5. checks if the minigames directory and server.jar exist before starting a minigame now
6. The "already connected to this server" message no longer appears for players already in the minigame when using /sln join <minigame> all.
7. Fixed a bug where minigame servers were not properly removed from BungeeCord when shutting down.
8. The viewAMinigameConfig method now checks if the minigame is in the config before attempting to view it.
9. I figured out how to use GitHub
