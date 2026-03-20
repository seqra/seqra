"""Top-level builder: mypy.build() → iterate modules → emit protobuf."""

from __future__ import annotations
from typing import Iterator
import os
import mypy.build
import mypy.options
from mypy.fscache import FileSystemCache
from mypy.nodes import MypyFile
from pir_server.proto import pir_pb2
from pir_server.builder.module_builder import ModuleBuilder


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

    def build(self) -> Iterator[pir_pb2.PIRModuleProto]:
        options = mypy.options.Options()
        if self.python_version:
            parts = self.python_version.split(".")
            if len(parts) >= 2:
                options.python_version = (int(parts[0]), int(parts[1]))
        options.ignore_missing_imports = "--ignore-missing-imports" in self.mypy_flags
        # Critical: disable incremental mode so ASTs are preserved in memory
        options.incremental = False
        options.preserve_asts = True
        options.export_types = True

        mypy_sources = []
        for s in self.sources:
            if os.path.isfile(s):
                # Derive module name from filename
                basename = os.path.basename(s)
                mod_name = os.path.splitext(basename)[0]
                mypy_sources.append(mypy.build.BuildSource(path=s, module=mod_name))
            elif os.path.isdir(s):
                for root, dirs, files in os.walk(s):
                    for f in files:
                        if f.endswith(".py"):
                            path = os.path.join(root, f)
                            # Derive module name from relative path
                            rel = os.path.relpath(path, os.path.dirname(s))
                            mod_name = rel.replace(os.sep, ".").removesuffix(".py")
                            if mod_name.endswith(".__init__"):
                                mod_name = mod_name.removesuffix(".__init__")
                            mypy_sources.append(
                                mypy.build.BuildSource(path=path, module=mod_name)
                            )

        if not mypy_sources:
            return

        fscache = FileSystemCache()
        try:
            result = mypy.build.build(
                sources=mypy_sources,
                options=options,
                fscache=fscache,
            )
        except Exception as e:
            import sys

            print(f"mypy build failed: {e}", file=sys.stderr)
            raise

        source_paths = set()
        for s in self.sources:
            source_paths.add(os.path.abspath(s))

        for module_name, state in result.graph.items():
            tree: MypyFile | None = state.tree
            if tree is None:
                continue
            if not self._should_include(state, source_paths):
                continue

            module_builder = ModuleBuilder(
                tree=tree,
                types=result.types,
                module_name=module_name,
            )
            yield module_builder.build()

    def _should_include(self, state, source_paths: set[str]) -> bool:
        if state.path is None:
            return False
        abs_path = os.path.abspath(state.path)
        for src in source_paths:
            if abs_path.startswith(src) or abs_path == src:
                return True
        return False
