"""Custom build: generates protobuf/gRPC stubs from proto/pir.proto before packaging."""

import os
import subprocess
import sys
from pathlib import Path

from setuptools import setup
from setuptools.command.build_py import build_py
from setuptools.command.develop import develop

PROTO_SRC = "proto/pir.proto"
PROTO_OUT = "pir_server/proto"


def generate_proto():
    """Run grpc_tools.protoc to generate pir_pb2.py and pir_pb2_grpc.py."""
    root = Path(__file__).parent
    proto_src = root / PROTO_SRC
    proto_out = root / PROTO_OUT

    if not proto_src.exists():
        print(
            f"WARNING: {proto_src} not found, skipping proto generation",
            file=sys.stderr,
        )
        return

    proto_out.mkdir(parents=True, exist_ok=True)

    cmd = [
        sys.executable,
        "-m",
        "grpc_tools.protoc",
        f"-I{root / 'proto'}",
        f"--python_out={proto_out}",
        f"--grpc_python_out={proto_out}",
        f"--pyi_out={proto_out}",
        str(proto_src),
    ]
    print(f"Generating proto stubs: {' '.join(cmd)}")
    subprocess.check_call(cmd)

    # Patch import in pir_pb2_grpc.py: bare "import pir_pb2" -> package-relative import
    grpc_stub = proto_out / "pir_pb2_grpc.py"
    if grpc_stub.exists():
        content = grpc_stub.read_text()
        content = content.replace(
            "import pir_pb2 as pir__pb2",
            "from pir_server.proto import pir_pb2 as pir__pb2",
        )
        grpc_stub.write_text(content)
        print("Patched pir_pb2_grpc.py imports")


class BuildPyWithProto(build_py):
    """Generate proto stubs before collecting Python packages."""

    def run(self):
        generate_proto()
        super().run()


class DevelopWithProto(develop):
    """Generate proto stubs for editable (develop) installs."""

    def run(self):
        generate_proto()
        super().run()


setup(
    cmdclass={
        "build_py": BuildPyWithProto,
        "develop": DevelopWithProto,
    },
)
