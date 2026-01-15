FROM golang:1.25-alpine AS builder

ARG OPENTAINT_BUILD_FLAGS

WORKDIR /app
COPY go.mod ./
COPY go.sum ./
RUN go mod download
COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="$OPENTAINT_BUILD_FLAGS" -o opentaint main.go

FROM opentaint-dependencies:latest

RUN useradd -ms /bin/bash opentaint

WORKDIR /home/opentaint

COPY --from=builder /app/opentaint /usr/local/bin/opentaint

RUN --mount=type=secret,id=github_token \
    opentaint pull --github-token=$(cat /run/secrets/github_token) --verbosity debug

CMD ["opentaint", "--help"]
