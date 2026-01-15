FROM golang:1.25-alpine AS builder

ARG SEQRA_BUILD_FLAGS

WORKDIR /app
COPY go.mod ./
COPY go.sum ./
RUN go mod download
COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="$SEQRA_BUILD_FLAGS" -o seqra main.go

FROM seqra-dependencies:latest

RUN useradd -ms /bin/bash seqra

WORKDIR /home/seqra

COPY --from=builder /app/seqra /usr/local/bin/seqra

RUN --mount=type=secret,id=github_token \
    seqra pull --github-token=$(cat /run/secrets/github_token) --verbosity debug

CMD ["seqra", "--help"]
