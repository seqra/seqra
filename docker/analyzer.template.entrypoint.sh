#!/bin/bash

env $ENTRY_POINT_ENV \
    "$JAVA_17_HOME"/bin/java \
      -Dorg.opentaint.ir.impl.storage.defaultBatchSize=2000 \
      -Djdk.util.jar.enableMultiRelease=false \
      -Xmx8g \
      -jar $ANALYZER_JAR_NAME "$@"
