package server

import (
	"context"
	"fmt"
	"time"

	pb "github.com/opentaint/go-ir/go-ssa-server/proto/goir"
)

type goSSAServer struct {
	pb.UnimplementedGoSSAServiceServer
	version   string
	goVersion string
}

func NewGoSSAServer(version, goVersion string) *goSSAServer {
	return &goSSAServer{version: version, goVersion: goVersion}
}

func (s *goSSAServer) Ping(ctx context.Context, req *pb.PingRequest) (*pb.PingResponse, error) {
	return &pb.PingResponse{
		Version:   s.version,
		GoVersion: s.goVersion,
	}, nil
}

func (s *goSSAServer) BuildProgram(req *pb.BuildProgramRequest, stream pb.GoSSAService_BuildProgramServer) error {
	startTime := time.Now()

	// 1. Build SSA
	prog, pkgs, err := buildSSA(req)
	if err != nil {
		return stream.Send(&pb.BuildProgramResponse{
			Payload: &pb.BuildProgramResponse_Error{
				Error: &pb.ProtoError{Message: err.Error(), Fatal: true},
			},
		})
	}

	// 2. Create serializer with ID allocator
	ser := newSerializer(prog, pkgs)

	// 3. Phase 1: Stream type definitions
	if err := ser.streamTypes(stream); err != nil {
		return fmt.Errorf("streaming types: %w", err)
	}

	// 4. Phase 2: Stream packages with member declarations
	if err := ser.streamPackages(stream); err != nil {
		return fmt.Errorf("streaming packages: %w", err)
	}

	// 5. Phase 3: Stream function bodies
	if err := ser.streamFunctionBodies(stream); err != nil {
		return fmt.Errorf("streaming function bodies: %w", err)
	}

	// 6. Send summary
	elapsed := time.Since(startTime)
	return stream.Send(&pb.BuildProgramResponse{
		Payload: &pb.BuildProgramResponse_Summary{
			Summary: &pb.ProtoBuildSummary{
				PackageCount:     int32(ser.stats.packageCount),
				FunctionCount:    int32(ser.stats.functionCount),
				TypeCount:        int32(ser.stats.typeCount),
				InstructionCount: int32(ser.stats.instructionCount),
				BuildTimeMs:      elapsed.Milliseconds(),
			},
		},
	})
}
