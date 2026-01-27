FROM sast-analyzer-dependencies:latest

RUN useradd -ms /bin/bash analyzer
WORKDIR /home/analyzer

ADD $DOCKER_IMAGE_CONTENT_PATH/ .
RUN chmod +x $DOCKER_ENTRYPOINT_SCRIPT

ENTRYPOINT ["./$DOCKER_ENTRYPOINT_SCRIPT"]
