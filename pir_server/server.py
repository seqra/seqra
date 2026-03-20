"""gRPC server lifecycle."""

import sys
import grpc
from concurrent import futures
from pir_server.service import PIRServiceServicer
from pir_server.proto import pir_pb2_grpc


def serve(port: int = 0):
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=1),
        options=[
            ("grpc.max_send_message_length", 256 * 1024 * 1024),
            ("grpc.max_receive_message_length", 256 * 1024 * 1024),
        ],
    )
    pir_pb2_grpc.add_PIRServiceServicer_to_server(PIRServiceServicer(), server)
    actual_port = server.add_insecure_port(f"localhost:{port}")
    server.start()

    sys.stdout.write(f"READY:{actual_port}\n")
    sys.stdout.flush()

    server.wait_for_termination()
