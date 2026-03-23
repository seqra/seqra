"""Top-level builder: mypy.build() -> iterate modules -> emit raw AST protobuf."""

from __future__ import annotations
from typing import Iterator
import os
import sys
import time
import mypy.build
import mypy.options
from mypy.errors import CompileError
from mypy.fscache import FileSystemCache
from mypy.nodes import MypyFile
from pir_server.proto import pir_pb2
from pir_server.builder.ast_serializer import AstSerializer


class ProjectBuilder:
    def __init__(
        self,
        sources: list[str],
        mypy_flags: list[str] | None = None,
        python_version: str | None = None,
        search_paths: list[str] | None = None,
    ):
        self.sources = sources
        self.mypy_flags = mypy_flags or []
        self.python_version = python_version
        self.search_paths = search_paths or []

    def build(self) -> Iterator[pir_pb2.MypyModuleProto]:
        """Build project and return raw mypy AST protos.

        Handles failures gracefully:
        - If mypy.build() raises CompileError, yields per-module error protos
          based on error messages (mapped to source files).
        - If individual module serialization fails, yields an error proto
          for that module and continues with the next one.
        - Logs progress every 10 seconds for large projects.
        """
        options = mypy.options.Options()
        if self.python_version:
            parts = self.python_version.split(".")
            if len(parts) >= 2:
                options.python_version = (int(parts[0]), int(parts[1]))
        options.ignore_missing_imports = "--ignore-missing-imports" in self.mypy_flags
        options.incremental = False
        options.preserve_asts = True
        options.export_types = True

        mypy_sources = []
        all_file_paths = []

        for s in self.sources:
            if os.path.isfile(s):
                all_file_paths.append(os.path.abspath(s))
            elif os.path.isdir(s):
                for root, dirs, files in os.walk(s):
                    for f in files:
                        if f.endswith(".py"):
                            all_file_paths.append(
                                os.path.abspath(os.path.join(root, f))
                            )

        search_root = self._find_search_root(all_file_paths)

        seen_modules: dict[str, str] = {}
        for path in all_file_paths:
            mod_name = self._path_to_module(path, search_root)
            if mod_name in seen_modules:
                print(
                    f"WARNING: Duplicate module '{mod_name}': "
                    f"{path} (already: {seen_modules[mod_name]}), skipping",
                    file=sys.stderr,
                )
                continue
            seen_modules[mod_name] = path
            mypy_sources.append(mypy.build.BuildSource(path=path, module=mod_name))

        if not mypy_sources:
            return

        print(f"PIR: Building {len(mypy_sources)} sources...", file=sys.stderr)

        fscache = FileSystemCache()
        try:
            result = mypy.build.build(
                sources=mypy_sources,
                options=options,
                fscache=fscache,
            )
        except CompileError as e:
            # mypy couldn't complete analysis. Map errors to modules by file path.
            yield from self._errors_to_unknown_modules(e.messages, seen_modules)
            return
        except Exception as e:
            # Unexpected mypy internal error (e.g. AssertionError inside mypy).
            # Try to identify the specific module that caused the failure.
            yield from self._exception_to_unknown_modules(e, seen_modules)
            return

        if result.errors:
            print(
                f"PIR: mypy reported {len(result.errors)} errors (non-fatal)",
                file=sys.stderr,
            )

        source_paths = set()
        for s in self.sources:
            source_paths.add(os.path.abspath(s))

        emitted = 0
        last_log = time.monotonic()
        total = sum(
            1
            for _, st in result.graph.items()
            if st.tree and self._should_include(st, source_paths)
        )

        for module_name, state in result.graph.items():
            tree: MypyFile | None = state.tree
            if tree is None:
                continue
            if not self._should_include(state, source_paths):
                continue

            # Progress logging every 10 seconds
            now = time.monotonic()
            if now - last_log >= 10.0:
                print(
                    f"PIR: Serializing module {emitted}/{total}: {module_name}",
                    file=sys.stderr,
                )
                last_log = now

            try:
                serializer = AstSerializer(
                    tree=tree,
                    types=result.types,
                    module_name=module_name,
                )
                yield serializer.serialize()
            except Exception as e:
                print(
                    f"WARNING: Failed to serialize module {module_name}: "
                    f"{type(e).__name__}: {e}",
                    file=sys.stderr,
                )
                yield pir_pb2.MypyModuleProto(
                    name=module_name,
                    path=getattr(tree, "path", ""),
                    errors=[f"{type(e).__name__}: {e}"],
                )

            emitted += 1

        print(f"PIR: Done. Emitted {emitted} modules.", file=sys.stderr)

    def _errors_to_unknown_modules(
        self, messages: list[str], seen_modules: dict[str, str]
    ) -> Iterator[pir_pb2.MypyModuleProto]:
        """Convert mypy CompileError messages to per-module error protos.

        Mypy error messages have the format: 'path/to/file.py:line: error: message'.
        We group by file path, map to module name, and yield one error proto per module.
        Errors that can't be mapped go to a synthetic '__build_errors__' module.
        """
        # Reverse map: path -> module_name (sorted longest first for best match)
        path_to_module = sorted(
            ((v, k) for k, v in seen_modules.items()),
            key=lambda x: -len(x[0]),
        )

        module_errors: dict[str, list[str]] = {}
        unmapped: list[str] = []

        for msg in messages:
            # Try to extract file path from error message (match by absolute or relative path)
            mapped = False
            for path, mod in path_to_module:
                if path in msg:
                    module_errors.setdefault(mod, []).append(msg)
                    mapped = True
                    break
            if not mapped:
                # Try relative path fragments
                for path, mod in path_to_module:
                    rel = os.path.relpath(path)
                    if rel in msg:
                        module_errors.setdefault(mod, []).append(msg)
                        mapped = True
                        break
            if not mapped:
                unmapped.append(msg)

        for mod_name, errors in module_errors.items():
            path = seen_modules.get(mod_name, "")
            yield pir_pb2.MypyModuleProto(
                name=mod_name,
                path=path,
                errors=errors,
            )

        if unmapped:
            yield pir_pb2.MypyModuleProto(
                name="__build_errors__",
                errors=unmapped,
            )

    def _exception_to_unknown_modules(
        self, exc: Exception, seen_modules: dict[str, str]
    ) -> Iterator[pir_pb2.MypyModuleProto]:
        """Convert an unexpected exception from mypy.build() to per-module error protos.

        Tries to identify the specific module that caused the failure by:
        1. Scanning the exception message for known module/class names
        2. Scanning the traceback for file paths matching source files
        3. Falling back to __build_errors__ if nothing matches
        """
        import traceback as tb

        error_msg = f"{type(exc).__name__}: {exc}"
        print(f"mypy build failed: {error_msg}", file=sys.stderr)
        tb.print_exc()

        exc_str = str(exc)
        tb_str = "".join(tb.format_exception(type(exc), exc, exc.__traceback__))

        # Strategy 1: Match dotted names in exception message against module names.
        # Use longest match first to find the most specific module.
        # e.g. "core.graphql.filters.DataFileFilter" should match
        # "core.graphql.filters" not just "core".
        best_match: str | None = None
        for mod_name in seen_modules:
            if mod_name in exc_str:
                if best_match is None or len(mod_name) > len(best_match):
                    best_match = mod_name
        if best_match:
            yield pir_pb2.MypyModuleProto(
                name=best_match,
                path=seen_modules[best_match],
                errors=[error_msg],
            )
            return

        # Strategy 3: Match file paths in traceback against source files
        for path, mod_name in sorted(
            ((v, k) for k, v in seen_modules.items()), key=lambda x: -len(x[0])
        ):
            if path in tb_str or os.path.relpath(path) in tb_str:
                yield pir_pb2.MypyModuleProto(
                    name=mod_name,
                    path=path,
                    errors=[error_msg],
                )
                return

        # Fallback: can't identify the module
        yield pir_pb2.MypyModuleProto(
            name="__build_errors__",
            errors=[error_msg],
        )

    def _find_search_root(self, file_paths: list[str]) -> str:
        """Find the best root directory for module name derivation."""
        if not file_paths:
            return "."

        dirs = set(os.path.dirname(p) for p in file_paths)
        roots = set()
        for d in dirs:
            pkg_root = d
            while True:
                parent = os.path.dirname(pkg_root)
                if parent == pkg_root:
                    break
                init_py = os.path.join(pkg_root, "__init__.py")
                if not os.path.isfile(init_py):
                    roots.add(pkg_root)
                    break
                pkg_root = parent
                if os.path.isfile(os.path.join(pkg_root, "__init__.py")):
                    continue
                else:
                    roots.add(pkg_root)
                    break

        if roots:
            return min(roots, key=len)
        return os.path.commonpath(file_paths)

    def _path_to_module(self, path: str, search_root: str) -> str:
        """Convert an absolute path to a Python module name relative to search_root."""
        rel = os.path.relpath(path, search_root)
        mod_name = rel.replace(os.sep, ".").removesuffix(".py")
        if mod_name.endswith(".__init__"):
            mod_name = mod_name.removesuffix(".__init__")
        return mod_name

    def _should_include(self, state, source_paths: set[str]) -> bool:
        if state.path is None:
            return False
        abs_path = os.path.abspath(state.path)
        for src in source_paths:
            if abs_path.startswith(src) or abs_path == src:
                return True
        return False
