"""Top-level builder: mypy.build() → iterate modules → emit raw AST protobuf."""

from __future__ import annotations
from typing import Iterator
import os
import mypy.build
import mypy.options
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
        """Build project and return raw mypy AST.

        This is the thin path: runs mypy, then serializes AST to proto.
        All complex lowering (CFG construction, expression flattening) is
        done on the Kotlin side.
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

        seen_modules: dict[str, str] = {}  # module_name -> first path
        for path in all_file_paths:
            mod_name = self._path_to_module(path, search_root)
            if mod_name in seen_modules:
                import sys

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

            serializer = AstSerializer(
                tree=tree,
                types=result.types,
                module_name=module_name,
            )
            yield serializer.serialize()

    def _find_search_root(self, file_paths: list[str]) -> str:
        """Find the best root directory for module name derivation.

        If the files belong to a Python package (a directory with __init__.py),
        we walk up from the package directory to find the topmost package,
        then use its parent as the search root.

        Example: for /site-packages/click/core.py, if click/ has __init__.py,
        the search root is /site-packages/, making the module name 'click.core'.
        """
        if not file_paths:
            return "."

        # Find directories of all files
        dirs = set(os.path.dirname(p) for p in file_paths)

        # For each unique directory, walk up while __init__.py exists
        roots = set()
        for d in dirs:
            pkg_root = d
            while True:
                parent = os.path.dirname(pkg_root)
                if parent == pkg_root:
                    break  # reached filesystem root
                init_py = os.path.join(pkg_root, "__init__.py")
                if not os.path.isfile(init_py):
                    # This directory is not a package; search root is here
                    roots.add(pkg_root)
                    break
                # It is a package; walk up
                pkg_root = parent
                if os.path.isfile(os.path.join(pkg_root, "__init__.py")):
                    continue
                else:
                    roots.add(pkg_root)
                    break

        # Use the shortest common root
        if roots:
            return min(roots, key=len)
        # Fallback: common directory of all files
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
