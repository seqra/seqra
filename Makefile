MAKE ?= make
GRADLEW := $(CURDIR)/core/gradlew
INSTALL ?= install

PREFIX ?= /usr/local
BINDIR ?= $(PREFIX)/bin
LIBDIR ?= $(PREFIX)/lib

CORE_DIR := core
CLI_DIR := cli

CLI_BINARY_NAME := opentaint
CLI_DEV_BINARY_NAME := opentaint-dev
ANALYZER_TASK := :projectAnalyzerJar
AUTOBUILDER_TASK := opentaint-jvm-autobuilder:projectAutoBuilderJar
TEST_UTIL_TASK := :opentaint-sast-test-util:jar

ANALYZER_JAR := $(CORE_DIR)/build/libs/opentaint-project-analyzer.jar
AUTOBUILDER_JAR := $(CORE_DIR)/opentaint-jvm-autobuilder/build/libs/opentaint-project-auto-builder.jar
TEST_UTIL_JAR := $(CORE_DIR)/opentaint-sast-test-util/build/libs/opentaint-sast-test-util.jar
INSTALLED_ANALYZER_JAR := $(LIBDIR)/$(notdir $(ANALYZER_JAR))
INSTALLED_AUTOBUILDER_JAR := $(LIBDIR)/$(notdir $(AUTOBUILDER_JAR))
INSTALLED_CLI_BINARY := $(BINDIR)/$(CLI_BINARY_NAME)
INSTALLED_DEV_BINARY := $(BINDIR)/$(CLI_DEV_BINARY_NAME)

.PHONY: all core projectAnalyzerJar core/autobuilder core/opentaint-sast-test-util cli install clean

all: core cli

core: projectAnalyzerJar core/autobuilder core/opentaint-sast-test-util

projectAnalyzerJar:
	cd $(CORE_DIR) && $(GRADLEW) $(ANALYZER_TASK)

core/autobuilder:
	cd $(CORE_DIR) && $(GRADLEW) $(AUTOBUILDER_TASK)

core/opentaint-sast-test-util:
	cd $(CORE_DIR) && $(GRADLEW) $(TEST_UTIL_TASK)

cli:
	$(MAKE) -C $(CLI_DIR) build

install: core cli
	mkdir -p $(BINDIR) $(LIBDIR)
	$(MAKE) -C $(CLI_DIR) install PREFIX=$(PREFIX) BINDIR=$(BINDIR)
	$(INSTALL) -m 0644 $(ANALYZER_JAR) $(INSTALLED_ANALYZER_JAR)
	$(INSTALL) -m 0644 $(AUTOBUILDER_JAR) $(INSTALLED_AUTOBUILDER_JAR)
	$(INSTALL) -m 0644 $(TEST_UTIL_JAR) $(LIBDIR)/$(notdir $(TEST_UTIL_JAR))
	printf '%s\n' \
		'#!/bin/sh' \
		'set -eu' \
		'BIN_DIR=$$(CDPATH= cd -- "$$(dirname -- "$$0")" && pwd)' \
		'PREFIX_DIR=$$(CDPATH= cd -- "$$BIN_DIR/.." && pwd)' \
		'LIB_DIR="$$PREFIX_DIR/lib"' \
		'exec "$$BIN_DIR/$(CLI_BINARY_NAME)" --experimental --analyzer-jar "$$LIB_DIR/$(notdir $(ANALYZER_JAR))" --autobuilder-jar "$$LIB_DIR/$(notdir $(AUTOBUILDER_JAR))" "$$@"' \
		> $(INSTALLED_DEV_BINARY)
	chmod 0755 $(INSTALLED_DEV_BINARY)

clean:
	$(MAKE) -C $(CLI_DIR) clean
	cd $(CORE_DIR) && $(GRADLEW) clean
