FROM ubuntu:24.04

RUN apt-get update && apt-get install -y -q --no-install-recommends ca-certificates curl wget apt-transport-https gpg unzip

# Java 8, 11, 17, latest (23)
RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null \
    && echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list \
    && apt update \
    && apt install -y -q --no-install-recommends temurin-8-jdk  && mv /usr/lib/jvm/temurin-8-jdk* /usr/lib/jvm/8-jdk \
    && apt install -y -q --no-install-recommends temurin-11-jdk && mv /usr/lib/jvm/temurin-11-jdk* /usr/lib/jvm/11-jdk \
    && apt install -y -q --no-install-recommends temurin-23-jdk && mv /usr/lib/jvm/temurin-23-jdk* /usr/lib/jvm/23-jdk \
    && apt install -y -q --no-install-recommends temurin-17-jdk && mv /usr/lib/jvm/temurin-17-jdk* /usr/lib/jvm/17-jdk \
    && rm /usr/bin/java && ln -s /usr/lib/jvm/17-jdk/bin/java /usr/bin/java \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_8_HOME=/usr/lib/jvm/8-jdk
ENV JAVA_11_HOME=/usr/lib/jvm/11-jdk
ENV JAVA_17_HOME=/usr/lib/jvm/17-jdk
ENV JAVA_LATEST_HOME=/usr/lib/jvm/23-jdk

# Gradle
ENV GRADLE_VERSION=gradle-8.10
RUN wget --no-verbose https://services.gradle.org/distributions/${GRADLE_VERSION}-bin.zip -O /tmp/${GRADLE_VERSION}-bin.zip \
    && unzip -q -d /opt/gradle /tmp/${GRADLE_VERSION}-bin.zip \
    && rm /tmp/${GRADLE_VERSION}-bin.zip
ENV PATH="/opt/gradle/${GRADLE_VERSION}/bin:${PATH}"

# Maven
ENV MAVEN_VERSION=3.9.11
RUN wget --no-verbose https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz -O /tmp/${MAVEN_VERSION}-bin.tar.gz \
    && mkdir /opt/maven && tar xzvf /tmp/${MAVEN_VERSION}-bin.tar.gz -C /opt/maven \
    && rm /tmp/${MAVEN_VERSION}-bin.tar.gz
ENV PATH="/opt/maven/apache-maven-${MAVEN_VERSION}/bin:${PATH}"

# Fix maven repository setting
RUN apt-get update && apt-get install -y -q --no-install-recommends \
                git inetutils-tools \
                && rm -rf /var/lib/apt/lists/* \
                && rm /opt/maven/*/conf/settings.xml \
                && ln -s /opt/maven/*/bin/mvn /usr/bin/mvn \
                && ln -s /opt/gradle/*/bin/gradle /usr/bin/gradle

RUN useradd -ms /bin/bash auto-builder
WORKDIR /home/auto-builder

ADD $DOCKER_IMAGE_CONTENT_PATH/ .
RUN chmod +x $DOCKER_ENTRYPOINT_SCRIPT

ENTRYPOINT ["./$DOCKER_ENTRYPOINT_SCRIPT"]
