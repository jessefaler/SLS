FROM        --platform=$TARGETOS/$TARGETARCH eclipse-temurin:17-jdk-jammy

LABEL       author="Matthew Penner" maintainer="matthew@pterodactyl.io"

LABEL       org.opencontainers.image.source="https://github.com/pterodactyl/yolks"
LABEL       org.opencontainers.image.licenses=MIT

#RUN 				apt-get update -y \
#						&& apt-get install -y lsof curl ca-certificates openssl git tar sqlite3 fontconfig libfreetype6 tzdata iproute2 libstdc++6 \
#						&& useradd -d /home/container -m container

#USER        container
#ENV         USER=container HOME=/home/container

COPY        ./../entrypoint.sh /entrypoint.sh
CMD         [ "/bin/bash", "/entrypoint.sh" ]

WORKDIR     /home/container/server/merged