"""PIRServiceServicer — implements all RPCs."""

import sys
import grpc
from pir_server.proto import pir_pb2, pir_pb2_grpc
from pir_server.builder.project_builder import ProjectBuilder
from pir_server.executor import execute_function


class PIRServiceServicer(pir_pb2_grpc.PIRServiceServicer):
    def BuildProject(self, request, context):
        """Stream PIRModuleProto messages, one per module."""
        try:
            builder = ProjectBuilder(
                sources=list(request.sources),
                mypy_flags=list(request.mypy_flags),
                python_version=request.python_version or None,
                search_paths=list(request.search_paths),
            )
            for module_proto in builder.build():
                yield module_proto
        except Exception as e:
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Build failed: {e}")
            import traceback

            traceback.print_exc()
            return

    def BuildModule(self, request, context):
        """Build a single module."""
        try:
            builder = ProjectBuilder(
                sources=[request.source_path],
                mypy_flags=list(request.mypy_flags),
                python_version=request.python_version or None,
                search_paths=list(request.search_paths),
            )
            for module_proto in builder.build():
                if module_proto.name == request.module_name:
                    return module_proto
            context.set_code(grpc.StatusCode.NOT_FOUND)
            context.set_details(f"Module '{request.module_name}' not found")
            return pir_pb2.PIRModuleProto()
        except Exception as e:
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return pir_pb2.PIRModuleProto()

    def ExecuteFunction(self, request, context):
        """Execute a Python function — used by Tier 3 tests."""
        return execute_function(request)

    def Ping(self, request, context):
        import mypy.version

        return pir_pb2.PingResponse(
            version="0.1.0",
            python_version=f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}",
            mypy_version=mypy.version.__version__,
        )

    def Shutdown(self, request, context):
        """Graceful shutdown."""
        import threading

        threading.Timer(0.1, lambda: sys.exit(0)).start()
        return pir_pb2.ShutdownResponse()
