#!/bin/bash

# Mount the overlay file system for the server
mkdir -p /home/container/server/workdir /home/container/server/merged /home/container/server/upperdir # Make directories for Copy On Write
mount -t overlay overlay -o lowerdir=/server,upperdir=/home/container/server/upperdir,workdir=/home/container/server/workdir /home/container/server/merged || { echo "Server Mount failed"; exit 1; }
# Mount the overlay file system for the world
mkdir -p /home/container/world/workdir /home/container/world/merged /home/container/world/upperdir # Make directories for Copy On Write
mount -t overlay overlay -o lowerdir=/world,upperdir=/home/container/world/upperdir,workdir=/home/container/world/workdir /home/container/world/merged || { echo "Server Mount failed"; exit 1; }

# Mount the merged world directory to the merged server directory at world so it can be accessed from world
mkdir -p /home/container/server/merged/world
mount --bind /home/container/world/merged /home/container/server/merged/world

# Default the TZ environment variable to UTC.
TZ=${TZ:-UTC}
export TZ

# Set environment variable that holds the Internal Docker IP
INTERNAL_IP=$(ip route get 1 | awk '{print $(NF-2);exit}')
export INTERNAL_IP

# Switch to the container's working directory
cd /home/container/server/merged || exit 1

# Print Java version
printf "\033[1m\033[33mcontainer@pterodactyl~ \033[0mjava -version\n"
java -version

# Convert all of the "{{VARIABLE}}" parts of the command into the expected shell
# variable format of "${VARIABLE}" before evaluating the string and automatically
# replacing the values.
PARSED=$(echo "${STARTUP}" | sed -e 's/{{/${/g' -e 's/}}/}/g' | eval echo "$(cat -)")

# Display the command we're running in the output, and then execute it with the env
# from the container itself.
printf "\033[1m\033[33mcontainer@pterodactyl~ \033[0m%s\n" "$PARSED"
# shellcheck disable=SC2086
exec env ${PARSED}
