"""Entry point: python -m pir_server [--port PORT]"""

import argparse
import sys
from pir_server.server import serve


def main():
    parser = argparse.ArgumentParser(description="PIR gRPC Server")
    parser.add_argument(
        "--port", type=int, default=0, help="Port to listen on (0 = auto-assign)"
    )
    args = parser.parse_args()
    serve(args.port)


if __name__ == "__main__":
    main()
