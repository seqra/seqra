#! /bin/bash

mkdir -p /cache/.m2      && ln -s /cache/.m2 ~/.m2
mkdir -p /cache/.gradle  && ln -s /cache/.gradle ~/.gradle

"$JAVA_17_HOME"/bin/java \
    -Xmx8g \
    -jar $AUTOBUILDER_JAR_NAME "$@"
