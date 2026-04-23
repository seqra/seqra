GO ?= go

BINARY_NAME ?= opentaint
BUILD_DIR ?= bin
BINARY_PATH := $(BUILD_DIR)/$(BINARY_NAME)

PREFIX ?= /usr/local
BINDIR ?= $(PREFIX)/bin
INSTALL_GOBIN := $(abspath $(BINDIR))

.PHONY: all generate build install clean

all: build

generate:
	$(GO) generate ./...

build: generate
	mkdir -p $(BUILD_DIR)
	$(GO) build -o $(BINARY_PATH) .

install: generate
	mkdir -p $(BINDIR)
	GOBIN=$(INSTALL_GOBIN) $(GO) install .

clean:
	rm -f $(BINARY_PATH)
