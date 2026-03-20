"""Builds PIRModuleProto from a single mypy MypyFile AST."""

from __future__ import annotations
from mypy.nodes import (
    MypyFile,
    FuncDef,
    ClassDef,
    AssignmentStmt,
    Decorator,
    OverloadedFuncDef,
    Var,
    NameExpr,
    Import,
    ImportFrom,
    ImportAll,
    ARG_POS,
    ARG_OPT,
    ARG_STAR,
    ARG_STAR2,
    ARG_NAMED,
    ARG_NAMED_OPT,
)
from mypy.types import CallableType
from pir_server.proto import pir_pb2
from pir_server.builder.statement_visitor import StatementTransformer
from pir_server.builder.type_mapper import TypeMapper
from pir_server.builder.scope import ScopeStack


class ModuleBuilder:
    def __init__(self, tree: MypyFile, types: dict, module_name: str):
        self.tree = tree
        self.types = types
        self.module_name = module_name
        self.type_mapper = TypeMapper()

    def build(self) -> pir_pb2.PIRModuleProto:
        proto = pir_pb2.PIRModuleProto(
            name=self.module_name,
            path=self.tree.path or "",
        )

        for defn in self.tree.defs:
            if isinstance(defn, ClassDef):
                proto.classes.append(self._build_class(defn))
            elif isinstance(defn, (FuncDef, Decorator, OverloadedFuncDef)):
                func_def = self._unwrap_func(defn)
                if func_def:
                    proto.functions.append(self._build_function(func_def, defn))
            elif isinstance(defn, AssignmentStmt):
                for lvalue in defn.lvalues:
                    field = self._build_field_from_assign(lvalue, defn)
                    if field:
                        proto.fields.append(field)

        proto.module_init.CopyFrom(self._build_module_init())
        proto.imports.extend(self._collect_imports())

        return proto

    def _build_class(self, class_def: ClassDef) -> pir_pb2.PIRClassProto:
        proto = pir_pb2.PIRClassProto(
            name=class_def.name,
            qualified_name=f"{self.module_name}.{class_def.name}",
        )

        if class_def.info:
            for base in class_def.info.bases:
                if hasattr(base, "type") and hasattr(base.type, "fullname"):
                    proto.base_classes.append(base.type.fullname)
            if hasattr(class_def.info, "mro") and class_def.info.mro:
                for mro_item in class_def.info.mro:
                    proto.mro.append(mro_item.fullname)
            proto.is_abstract = class_def.info.is_abstract

        for defn in class_def.defs.body:
            if isinstance(defn, (FuncDef, Decorator, OverloadedFuncDef)):
                func_def = self._unwrap_func(defn)
                if func_def:
                    func_proto = self._build_function(func_def, defn)
                    # Set flags for methods
                    if isinstance(defn, Decorator):
                        for dec in defn.decorators:
                            if isinstance(dec, NameExpr):
                                if dec.name == "staticmethod":
                                    func_proto.is_static_method = True
                                elif dec.name == "classmethod":
                                    func_proto.is_class_method = True
                                elif dec.name == "property":
                                    func_proto.is_property = True
                    proto.methods.append(func_proto)
            elif isinstance(defn, AssignmentStmt):
                for lvalue in defn.lvalues:
                    field = self._build_field_from_assign(lvalue, defn)
                    if field:
                        proto.fields.append(field)
            elif isinstance(defn, ClassDef):
                proto.nested_classes.append(self._build_class(defn))

        return proto

    def _build_function(
        self, func_def: FuncDef, orig_defn=None
    ) -> pir_pb2.PIRFunctionProto:
        scope = ScopeStack()
        stmt_visitor = StatementTransformer(
            types=self.types,
            type_mapper=self.type_mapper,
            scope=scope,
        )
        try:
            cfg_proto = stmt_visitor.build_function_cfg(func_def)
        except Exception:
            cfg_proto = pir_pb2.PIRCFGProto(
                blocks=[
                    pir_pb2.PIRBasicBlockProto(
                        label=0,
                        instructions=[
                            pir_pb2.PIRInstructionProto(
                                return_inst=pir_pb2.PIRReturnProto()
                            )
                        ],
                    )
                ],
                entry_block=0,
                exit_blocks=[0],
            )

        proto = pir_pb2.PIRFunctionProto(
            name=func_def.name,
            qualified_name=f"{self.module_name}.{func_def.name}",
            cfg=cfg_proto,
            is_async=func_def.is_coroutine,
            is_generator=func_def.is_generator,
        )

        for arg in func_def.arguments:
            proto.parameters.append(self._build_parameter(arg))

        func_type = func_def.type
        if isinstance(func_type, CallableType):
            proto.return_type.CopyFrom(self.type_mapper.map(func_type.ret_type))

        if isinstance(orig_defn, Decorator):
            for dec in orig_defn.decorators:
                dec_proto = pir_pb2.PIRDecoratorProto()
                if isinstance(dec, NameExpr):
                    dec_proto.name = dec.name
                    if dec.node and hasattr(dec.node, "fullname"):
                        dec_proto.qualified_name = dec.node.fullname or ""
                proto.decorators.append(dec_proto)

        return proto

    def _build_parameter(self, arg) -> pir_pb2.PIRParameterProto:
        kind_map = {
            ARG_POS: pir_pb2.POSITIONAL_OR_KEYWORD,
            ARG_OPT: pir_pb2.POSITIONAL_OR_KEYWORD,
            ARG_STAR: pir_pb2.VAR_POSITIONAL,
            ARG_STAR2: pir_pb2.VAR_KEYWORD,
            ARG_NAMED: pir_pb2.KEYWORD_ONLY,
            ARG_NAMED_OPT: pir_pb2.KEYWORD_ONLY,
        }
        proto = pir_pb2.PIRParameterProto(
            name=arg.variable.name if arg.variable else "",
            kind=kind_map.get(arg.kind, pir_pb2.POSITIONAL_OR_KEYWORD),
            has_default=arg.initializer is not None,
        )
        if arg.variable and arg.variable.type:
            proto.type.CopyFrom(self.type_mapper.map(arg.variable.type))
        return proto

    def _build_field_from_assign(self, lvalue, stmt) -> pir_pb2.PIRFieldProto | None:
        if isinstance(lvalue, NameExpr):
            field = pir_pb2.PIRFieldProto(
                name=lvalue.name,
                has_initializer=True,
            )
            if lvalue.node and hasattr(lvalue.node, "type") and lvalue.node.type:
                field.type.CopyFrom(self.type_mapper.map(lvalue.node.type))
            return field
        return None

    def _build_module_init(self) -> pir_pb2.PIRFunctionProto:
        scope = ScopeStack()
        stmt_visitor = StatementTransformer(
            types=self.types,
            type_mapper=self.type_mapper,
            scope=scope,
        )
        try:
            cfg_proto = stmt_visitor.build_module_init_cfg(self.tree)
        except Exception:
            cfg_proto = pir_pb2.PIRCFGProto(
                blocks=[
                    pir_pb2.PIRBasicBlockProto(
                        label=0,
                        instructions=[
                            pir_pb2.PIRInstructionProto(
                                return_inst=pir_pb2.PIRReturnProto()
                            )
                        ],
                    )
                ],
                entry_block=0,
                exit_blocks=[0],
            )
        return pir_pb2.PIRFunctionProto(
            name="__module_init__",
            qualified_name=f"{self.module_name}.__module_init__",
            cfg=cfg_proto,
        )

    def _unwrap_func(self, defn) -> FuncDef | None:
        if isinstance(defn, FuncDef):
            return defn
        elif isinstance(defn, Decorator):
            return defn.func
        elif isinstance(defn, OverloadedFuncDef):
            if defn.items:
                return self._unwrap_func(defn.items[0])
        return None

    def _collect_imports(self) -> list[str]:
        imports = []
        for defn in self.tree.defs:
            if isinstance(defn, Import):
                for mod_id, _ in defn.ids:
                    imports.append(mod_id)
            elif isinstance(defn, ImportFrom):
                imports.append(defn.id)
            elif isinstance(defn, ImportAll):
                imports.append(defn.id)
        return imports
