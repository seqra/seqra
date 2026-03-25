package main

import (
	"flag"
	"fmt"
	"net"
	"os"
	"runtime"

	"google.golang.org/grpc"

	server "github.com/opentaint/go-ir/go-ssa-server"
	pb "github.com/opentaint/go-ir/go-ssa-server/proto/goir"
)

const version = "0.1.0"

func main() {
	port := flag.Int("port", 0, "port to listen on (0 = random)")
	flag.Parse()

	lis, err := net.Listen("tcp", fmt.Sprintf("localhost:%d", *port))
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR:%v\n", err)
		os.Exit(1)
	}

	// Print the port so the Kotlin client can read it
	fmt.Printf("LISTENING:%d\n", lis.Addr().(*net.TCPAddr).Port)

	srv := grpc.NewServer()
	pb.RegisterGoSSAServiceServer(srv, server.NewGoSSAServer(version, runtime.Version()))
	if err := srv.Serve(lis); err != nil {
		fmt.Fprintf(os.Stderr, "ERROR:%v\n", err)
		os.Exit(1)
	}
}
