"""Execute Python functions for Tier 3 round-trip testing."""

import json
import textwrap
from pir_server.proto import pir_pb2


def execute_function(
    request: pir_pb2.ExecuteFunctionRequest,
) -> pir_pb2.ExecuteFunctionResponse:
    source = textwrap.dedent(request.source_code)
    func_name = request.function_name
    inputs = json.loads(request.arguments_json)

    namespace = {}
    exec(source, namespace)
    func = namespace[func_name]

    results = []
    for args, kwargs in inputs:
        try:
            value = func(*args, **kwargs)
            results.append({"value": _serialize(value), "exception": None})
        except Exception as e:
            results.append({"value": None, "exception": type(e).__name__})

    return pir_pb2.ExecuteFunctionResponse(results_json=json.dumps(results))


def _serialize(value):
    """Best-effort JSON serialization of Python values."""
    if isinstance(value, (int, float, str, bool, type(None))):
        return value
    if isinstance(value, (list, tuple)):
        return [_serialize(v) for v in value]
    if isinstance(value, dict):
        return {str(k): _serialize(v) for k, v in value.items()}
    if isinstance(value, set):
        return sorted(_serialize(v) for v in value)
    return str(value)
