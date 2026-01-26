FROM opentaint-jvm-autobuilder/sast-autobuilder-dependencies:latest

RUN useradd -ms /bin/bash auto-builder
WORKDIR /home/auto-builder

ADD $DOCKER_IMAGE_CONTENT_PATH/ .
RUN chmod +x $DOCKER_ENTRYPOINT_SCRIPT

ENTRYPOINT ["./$DOCKER_ENTRYPOINT_SCRIPT"]
