FROM ubuntu:24.04

RUN apt-get update && apt-get install -y -q --no-install-recommends ca-certificates curl wget apt-transport-https gpg unzip language-pack-en \
    && update-locale LANG=en_US.UTF-8
ENV LC_ALL=en_US.UTF-8

# Java 17
RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null \
    && echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list \
    && apt update \
    && apt install -y -q --no-install-recommends temurin-17-jdk && mv /usr/lib/jvm/temurin-17-jdk* /usr/lib/jvm/17-jdk \
    && rm /usr/bin/java && ln -s /usr/lib/jvm/17-jdk/bin/java /usr/bin/java \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_17_HOME=/usr/lib/jvm/17-jdk

RUN useradd -ms /bin/bash analyzer
WORKDIR /home/analyzer

ADD $DOCKER_IMAGE_CONTENT_PATH/ .
RUN chmod +x $DOCKER_ENTRYPOINT_SCRIPT

ENTRYPOINT ["./$DOCKER_ENTRYPOINT_SCRIPT"]
