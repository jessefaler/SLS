#!/bin/bash
# Paper Installation Script
PROJECT=paper

if [ -n "${DL_PATH}" ]; then
    echo -e "Using supplied download url: ${DL_PATH}"
    DOWNLOAD_URL=`eval echo $(echo ${DL_PATH} | sed -e 's/{{/${/g' -e 's/}}/}/g')`
else
    VER_EXISTS=`curl -s https://api.papermc.io/v2/projects/${PROJECT} | jq -r --arg VERSION $MINECRAFT_VERSION '.versions[] | contains($VERSION)' | grep -m1 true`
    LATEST_VERSION=`curl -s https://api.papermc.io/v2/projects/${PROJECT} | jq -r '.versions' | jq -r '.[-1]'`

    if [ "${VER_EXISTS}" == "true" ]; then
        echo -e "Version is valid. Using version ${MINECRAFT_VERSION}"
    else
        echo -e "Specified version not found. Defaulting to the latest ${PROJECT} version"
        MINECRAFT_VERSION=${LATEST_VERSION}
    fi

    BUILD_EXISTS=`curl -s https://api.papermc.io/v2/projects/${PROJECT}/versions/${MINECRAFT_VERSION} | jq -r --arg BUILD ${BUILD_NUMBER} '.builds[] | tostring | contains($BUILD)' | grep -m1 true`
    LATEST_BUILD=`curl -s https://api.papermc.io/v2/projects/${PROJECT}/versions/${MINECRAFT_VERSION} | jq -r '.builds' | jq -r '.[-1]'`

    if [ "${BUILD_EXISTS}" == "true" ]; then
        echo -e "Build is valid for version ${MINECRAFT_VERSION}. Using build ${BUILD_NUMBER}"
    else
        echo -e "Using the latest ${PROJECT} build for version ${MINECRAFT_VERSION}"
        BUILD_NUMBER=${LATEST_BUILD}
    fi

    JAR_NAME=${PROJECT}-${MINECRAFT_VERSION}-${BUILD_NUMBER}.jar
    echo "Version being downloaded"
    echo -e "MC Version: ${MINECRAFT_VERSION}"
    echo -e "Build: ${BUILD_NUMBER}"
    echo -e "JAR Name of Build: ${JAR_NAME}"
    DOWNLOAD_URL=https://api.papermc.io/v2/projects/${PROJECT}/versions/${MINECRAFT_VERSION}/builds/${BUILD_NUMBER}/downloads/${JAR_NAME}
fi

cd /home/container

echo -e "Running curl -o ${SERVER_JARFILE} ${DOWNLOAD_URL}"
if [ -f ${SERVER_JARFILE} ]; then
    mv ${SERVER_JARFILE} ${SERVER_JARFILE}.old
fi

curl -f -o ${SERVER_JARFILE} ${DOWNLOAD_URL}
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to download server jar. Aborting."
    exit 1
fi

if [ ! -f ${SERVER_JARFILE} ]; then
    echo "ERROR: Server jar not found after download. Aborting."
    exit 1
fi

# Wite the default server properties file
cat > server.properties <<EOF
server-port=25565
server-ip=
max-players=50
motd=SLS
allow-nether=false
online-mode=false
enable-command-block=true
spawn-protection=0
view-distance=12
EOF

# Wite the default bukkit config file
cat > bukkit.yml <<EOF
settings:
  allow-end: false
EOF

printf "\033[1m\033[33mcontainer@sls~ \033[0mjava -version\n"
java -version

echo "eula=true" > eula.txt

FIFO="server.pipe"
mkfifo $FIFO

echo "Starting server for warmup..."
java -Xms128M -XX:MaxRAMPercentage=95.0 -Dterminal.jline=false -Dterminal.ansi=true \
    -jar ${SERVER_JARFILE} -nogui < $FIFO > server-output.log 2>&1 &
SERVER_PID=$!

# Check server actually started
sleep 2
if ! kill -0 $SERVER_PID 2>/dev/null; then
    echo "ERROR: Server process failed to start. Aborting."
    cat server-output.log
    exit 1
fi

# Keep write end of pipe open so server doesn't block on stdin
exec 3>$FIFO

START_TIME=$(date +%s)
TIMEOUT=500
SUCCESS=false

echo "Waiting for server to report Done..."

tail -F server-output.log 2>/dev/null | while read -r line; do
    echo "[SERVER] $line"
done &
TAIL_PID=$!

while true; do
    if [ -f server-output.log ] && grep -q "Preparing level" server-output.log; then
        echo "Server finished starting. Sending stop..."
        echo "stop" > $FIFO
        SUCCESS=true
        break
    fi

    # Check if server died unexpectedly
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "ERROR: Server process died unexpectedly. Aborting."
        cat server-output.log
        exit 1
    fi

    NOW=$(date +%s)
    ELAPSED=$((NOW - START_TIME))
    if [ "$ELAPSED" -ge "$TIMEOUT" ]; then
        echo "ERROR: Timeout reached waiting for server. Aborting."
        kill -9 $SERVER_PID 2>/dev/null
        cat server-output.log
        exit 1
    fi

    sleep 2
done

# Wait for server to fully shut down
wait $SERVER_PID

# Cleanup
exec 3>&-
kill $TAIL_PID 2>/dev/null || true
rm -f $FIFO
rm -f server-output.log

# Clean up server-generated files not needed in the image
rm -rf world world_nether world_the_end logs

if [ "$SUCCESS" = true ]; then
    echo "Warmup complete."
    exit 0
else
    echo "ERROR: Warmup did not complete successfully."
    exit 1
fi