"""Thin AST serializer: walks mypy AST and serializes to MypyModuleProto.

No CFG construction, no expression flattening — just 1:1 serialization.
All complex lowering is done in Kotlin.
"""

from __future__ import annotations
import sys
from mypy.nodes import (
    MypyFile,
    FuncDef,
    ClassDef,
    AssignmentStmt,
    Decorator,
    OverloadedFuncDef,
    Var,
    NameExpr,
    MemberExpr,
    Import,
    ImportFrom,
    ImportAll,
    Block,
    ExpressionStmt,
    ReturnStmt,
    IfStmt,
    WhileStmt,
    ForStmt,
    TryStmt,
    WithStmt,
    RaiseStmt,
    BreakStmt,
    ContinueStmt,
    DelStmt,
    AssertStmt,
    PassStmt,
    GlobalDecl,
    NonlocalDecl,
    OperatorAssignmentStmt,
    IntExpr,
    StrExpr,
    FloatExpr,
    BytesExpr,
    ComplexExpr,
    EllipsisExpr,
    CallExpr,
    OpExpr,
    UnaryExpr,
    ComparisonExpr,
    IndexExpr,
    SliceExpr,
    ListExpr,
    TupleExpr,
    SetExpr,
    DictExpr,
    ConditionalExpr,
    StarExpr,
    YieldExpr,
    YieldFromExpr,
    AwaitExpr,
    AssignmentExpr,
    LambdaExpr,
    SuperExpr,
    ListComprehension,
    SetComprehension,
    DictionaryComprehension,
    GeneratorExpr,
    Expression,
    ARG_POS,
    ARG_OPT,
    ARG_STAR,
    ARG_STAR2,
    ARG_NAMED,
    ARG_NAMED_OPT,
)
from mypy.types import CallableType
from pir_server.proto import pir_pb2
from pir_server.builder.type_mapper import TypeMapper


class AstSerializer:
    """Serializes a mypy MypyFile AST to MypyModuleProto.

    This is the thin Python-side wrapper. It does NOT:
    - Construct CFGs
    - Flatten expressions
    - Generate temporary variables
    - Lower control flow

    It DOES:
    - Walk the mypy AST tree
    - Serialize each node to its proto counterpart
    - Map mypy types to PIRTypeProto (reuses TypeMapper)
    - Extract class metadata (base classes, MRO, dataclass/enum/abstract flags)
    - Resolve symbol fullnames from mypy's symbol table
    """

    def __init__(self, tree: MypyFile, types: dict, module_name: str):
        self.tree = tree
        self.types = types
        self.module_name = module_name
        self.type_mapper = TypeMapper()

    def serialize(self) -> pir_pb2.MypyModuleProto:
        proto = pir_pb2.MypyModuleProto(
            name=self.module_name,
            path=self.tree.path or "",
        )
        for defn in self.tree.defs:
            try:
                for d in self._serialize_definitions(defn):
                    proto.defs.append(d)
            except Exception as e:
                import sys

                print(
                    f"WARNING: Failed to serialize definition in {self.module_name}: "
                    f"{type(e).__name__}: {e}",
                    file=sys.stderr,
                )
        proto.imports.extend(self._collect_imports())
        return proto

    # ─── Definitions ──────────────────────────────────────

    def _serialize_definitions(
        self, defn, enclosing_class: str | None = None
    ) -> list[pir_pb2.MypyDefinitionProto]:
        """Serialize a definition, returning a list (usually 1 item, but OverloadedFuncDef may produce multiple)."""
        if isinstance(defn, ClassDef):
            return [
                pir_pb2.MypyDefinitionProto(class_def=self._serialize_class_def(defn))
            ]
        elif isinstance(defn, Decorator):
            return [
                pir_pb2.MypyDefinitionProto(
                    decorator=self._serialize_decorator_def(defn, enclosing_class)
                )
            ]
        elif isinstance(defn, FuncDef):
            return [
                pir_pb2.MypyDefinitionProto(
                    func_def=self._serialize_func_def(defn, enclosing_class)
                )
            ]
        elif isinstance(defn, OverloadedFuncDef):
            # Serialize ALL items (e.g. property getter + setter + deleter)
            results = []
            for item in defn.items:
                results.extend(self._serialize_definitions(item, enclosing_class))
            return results
        elif isinstance(defn, AssignmentStmt):
            return [
                pir_pb2.MypyDefinitionProto(
                    assignment=self._serialize_assignment_stmt(defn)
                )
            ]
        return []

    def _serialize_class_def(self, class_def: ClassDef) -> pir_pb2.MypyClassDefProto:
        proto = pir_pb2.MypyClassDefProto(
            name=class_def.name,
            fullname=f"{self.module_name}.{class_def.name}",
        )

        if class_def.info:
            for base in class_def.info.bases:
                if hasattr(base, "type") and hasattr(base.type, "fullname"):
                    proto.base_classes.append(base.type.fullname)
            if hasattr(class_def.info, "mro") and class_def.info.mro:
                for mro_item in class_def.info.mro:
                    proto.mro.append(mro_item.fullname)
            proto.is_abstract = class_def.info.is_abstract
            # Detect dataclass
            if (
                hasattr(class_def.info, "metadata")
                and "dataclass" in class_def.info.metadata
            ):
                proto.is_dataclass = True
            elif any(
                hasattr(d, "name") and d.name == "dataclass"
                for d in getattr(class_def, "decorators", [])
            ):
                proto.is_dataclass = True
            # Detect enum
            for base in class_def.info.bases:
                if hasattr(base, "type") and hasattr(base.type, "fullname"):
                    if base.type.fullname in (
                        "enum.Enum",
                        "enum.IntEnum",
                        "enum.Flag",
                        "enum.IntFlag",
                    ):
                        proto.is_enum = True
                        break

        # Class body
        for defn in class_def.defs.body:
            for d in self._serialize_definitions(defn, enclosing_class=class_def.name):
                proto.body.append(d)

        return proto

    def _serialize_func_def(
        self, func_def: FuncDef, enclosing_class: str | None = None
    ) -> pir_pb2.MypyFuncDefProto:
        if enclosing_class:
            fullname = f"{self.module_name}.{enclosing_class}.{func_def.name}"
        else:
            fullname = f"{self.module_name}.{func_def.name}"

        proto = pir_pb2.MypyFuncDefProto(
            name=func_def.name,
            fullname=fullname,
            is_async=func_def.is_coroutine,
            is_generator=func_def.is_generator,
            is_static=getattr(func_def, "is_static", False),
            is_class=getattr(func_def, "is_class", False),
            is_property=getattr(func_def, "is_property", False),
            line=getattr(func_def, "line", -1),
        )

        # Body
        if func_def.body:
            proto.body.CopyFrom(self._serialize_block(func_def.body))

        # Arguments
        for arg in func_def.arguments:
            proto.arguments.append(self._serialize_argument(arg))

        # Return type
        func_type = func_def.type
        if isinstance(func_type, CallableType):
            proto.return_type.CopyFrom(self.type_mapper.map(func_type.ret_type))

        return proto

    def _serialize_decorator_def(
        self, dec: Decorator, enclosing_class: str | None = None
    ) -> pir_pb2.MypyDecoratorDefProto:
        proto = pir_pb2.MypyDecoratorDefProto(
            func=self._serialize_func_def(dec.func, enclosing_class),
            name=dec.func.name,
        )
        # Serialize decorator expressions
        for d in dec.decorators:
            proto.original_decorators.append(self._serialize_expr(d))
        # Extract qualified name
        if dec.func.fullname:
            proto.qualified_name = dec.func.fullname
        return proto

    def _serialize_argument(self, arg) -> pir_pb2.MypyArgumentProto:
        proto = pir_pb2.MypyArgumentProto(
            name=arg.variable.name if arg.variable else "",
            kind=int(arg.kind.value),
            has_default=arg.initializer is not None,
        )
        if arg.variable and arg.variable.type:
            proto.type.CopyFrom(self.type_mapper.map(arg.variable.type))
        if arg.initializer:
            proto.default_value.CopyFrom(self._serialize_expr(arg.initializer))
        return proto

    # ─── Blocks & Statements ──────────────────────────────

    def _serialize_block(self, block: Block) -> pir_pb2.MypyBlockProto:
        proto = pir_pb2.MypyBlockProto()
        # Collect names of nested FuncDef/Decorator in this block
        # so nested functions can exclude sibling names from closure_vars
        sibling_func_names = set()
        for stmt in block.body:
            func_def = (
                self._unwrap_func(stmt)
                if isinstance(stmt, (FuncDef, Decorator, OverloadedFuncDef))
                else None
            )
            if func_def:
                sibling_func_names.add(func_def.name)
        for stmt in block.body:
            s = self._serialize_stmt(stmt, sibling_func_names)
            if s is not None:
                proto.stmts.append(s)
        return proto

    def _serialize_stmt(
        self, stmt, sibling_func_names: set[str] | None = None
    ) -> pir_pb2.MypyStmtProto | None:
        line = getattr(stmt, "line", -1)
        col = getattr(stmt, "column", 0)
        proto = pir_pb2.MypyStmtProto(line=line, col=col)

        if isinstance(stmt, AssignmentStmt):
            proto.assignment.CopyFrom(self._serialize_assignment_stmt(stmt))
        elif isinstance(stmt, OperatorAssignmentStmt):
            proto.op_assignment.CopyFrom(
                pir_pb2.MypyOperatorAssignmentStmtProto(
                    op=stmt.op,
                    lvalue=self._serialize_expr(stmt.lvalue),
                    rvalue=self._serialize_expr(stmt.rvalue),
                )
            )
        elif isinstance(stmt, ExpressionStmt):
            proto.expression_stmt.CopyFrom(
                pir_pb2.MypyExpressionStmtProto(expr=self._serialize_expr(stmt.expr))
            )
        elif isinstance(stmt, ReturnStmt):
            ret = pir_pb2.MypyReturnStmtProto()
            if stmt.expr:
                ret.expr.CopyFrom(self._serialize_expr(stmt.expr))
            proto.return_stmt.CopyFrom(ret)
        elif isinstance(stmt, IfStmt):
            if_proto = pir_pb2.MypyIfStmtProto()
            for cond in stmt.expr:
                if_proto.conditions.append(self._serialize_expr(cond))
            for body in stmt.body:
                if_proto.bodies.append(self._serialize_block(body))
            if stmt.else_body:
                if_proto.else_body.CopyFrom(self._serialize_block(stmt.else_body))
            proto.if_stmt.CopyFrom(if_proto)
        elif isinstance(stmt, WhileStmt):
            while_proto = pir_pb2.MypyWhileStmtProto(
                condition=self._serialize_expr(stmt.expr),
                body=self._serialize_block(stmt.body),
            )
            if stmt.else_body:
                while_proto.else_body.CopyFrom(self._serialize_block(stmt.else_body))
            proto.while_stmt.CopyFrom(while_proto)
        elif isinstance(stmt, ForStmt):
            for_proto = pir_pb2.MypyForStmtProto(
                index=self._serialize_expr(stmt.index),
                iterable=self._serialize_expr(stmt.expr),
                body=self._serialize_block(stmt.body),
            )
            if stmt.else_body:
                for_proto.else_body.CopyFrom(self._serialize_block(stmt.else_body))
            proto.for_stmt.CopyFrom(for_proto)
        elif isinstance(stmt, TryStmt):
            try_proto = pir_pb2.MypyTryStmtProto(
                body=self._serialize_block(stmt.body),
            )
            for t in stmt.types:
                if t is not None:
                    try_proto.types.append(self._serialize_expr(t))
                else:
                    # Bare except — empty expr
                    try_proto.types.append(pir_pb2.MypyExprProto())
            for v in stmt.vars:
                if v is not None:
                    try_proto.vars.append(self._serialize_expr(v))
                else:
                    try_proto.vars.append(pir_pb2.MypyExprProto())
            for h in stmt.handlers:
                try_proto.handlers.append(self._serialize_block(h))
            if stmt.else_body:
                try_proto.else_body.CopyFrom(self._serialize_block(stmt.else_body))
            if stmt.finally_body:
                try_proto.finally_body.CopyFrom(
                    self._serialize_block(stmt.finally_body)
                )
            proto.try_stmt.CopyFrom(try_proto)
        elif isinstance(stmt, WithStmt):
            with_proto = pir_pb2.MypyWithStmtProto(
                body=self._serialize_block(stmt.body),
                is_async=getattr(stmt, "is_async", False),
            )
            for expr in stmt.expr:
                with_proto.exprs.append(self._serialize_expr(expr))
            if stmt.target:
                for t in stmt.target:
                    if t is not None:
                        with_proto.targets.append(self._serialize_expr(t))
                    else:
                        with_proto.targets.append(pir_pb2.MypyExprProto())
            proto.with_stmt.CopyFrom(with_proto)
        elif isinstance(stmt, RaiseStmt):
            raise_proto = pir_pb2.MypyRaiseStmtProto()
            if stmt.expr:
                raise_proto.expr.CopyFrom(self._serialize_expr(stmt.expr))
            if stmt.from_expr:
                raise_proto.from_expr.CopyFrom(self._serialize_expr(stmt.from_expr))
            proto.raise_stmt.CopyFrom(raise_proto)
        elif isinstance(stmt, BreakStmt):
            proto.break_stmt.CopyFrom(pir_pb2.MypyBreakStmtProto())
        elif isinstance(stmt, ContinueStmt):
            proto.continue_stmt.CopyFrom(pir_pb2.MypyContinueStmtProto())
        elif isinstance(stmt, DelStmt):
            proto.del_stmt.CopyFrom(
                pir_pb2.MypyDelStmtProto(expr=self._serialize_expr(stmt.expr))
            )
        elif isinstance(stmt, AssertStmt):
            assert_proto = pir_pb2.MypyAssertStmtProto(
                expr=self._serialize_expr(stmt.expr)
            )
            if stmt.msg:
                assert_proto.msg.CopyFrom(self._serialize_expr(stmt.msg))
            proto.assert_stmt.CopyFrom(assert_proto)
        elif isinstance(stmt, PassStmt):
            proto.pass_stmt.CopyFrom(pir_pb2.MypyPassStmtProto())
        elif isinstance(stmt, GlobalDecl):
            proto.global_decl.CopyFrom(
                pir_pb2.MypyGlobalDeclProto(names=list(stmt.names))
            )
        elif isinstance(stmt, (FuncDef, Decorator, OverloadedFuncDef)):
            func_def = self._unwrap_func(stmt)
            if func_def:
                func_proto = self._serialize_func_def(func_def)
                # Populate closure_vars for nested function definitions
                free_vars = self._collect_free_vars(func_def, sibling_func_names)
                for fv in free_vars:
                    func_proto.closure_vars.append(fv)
                proto.func_def.CopyFrom(func_proto)
        elif isinstance(stmt, ClassDef):
            proto.class_def.CopyFrom(self._serialize_class_def(stmt))
        elif isinstance(stmt, Block):
            # Inline block — serialize each statement
            # Return None and let caller handle
            return None
        else:
            return None

        return proto

    def _serialize_assignment_stmt(
        self, stmt: AssignmentStmt
    ) -> pir_pb2.MypyAssignmentStmtProto:
        proto = pir_pb2.MypyAssignmentStmtProto(
            rvalue=self._serialize_expr(stmt.rvalue),
        )
        for lvalue in stmt.lvalues:
            proto.lvalues.append(self._serialize_expr(lvalue))
        return proto

    # ─── Expressions ──────────────────────────────────────

    MAX_EXPR_DEPTH = 40

    def _serialize_expr(self, expr: Expression) -> pir_pb2.MypyExprProto:
        if expr is None:
            return pir_pb2.MypyExprProto()

        self._expr_depth = getattr(self, "_expr_depth", 0) + 1
        try:
            if self._expr_depth > self.MAX_EXPR_DEPTH:
                # Bail out for deeply nested expression trees (e.g., idna's huge | chains)
                return pir_pb2.MypyExprProto()
            return self._serialize_expr_inner(expr)
        finally:
            self._expr_depth -= 1

    def _serialize_expr_inner(self, expr: Expression) -> pir_pb2.MypyExprProto:
        line = getattr(expr, "line", -1)
        col = getattr(expr, "column", 0)
        proto = pir_pb2.MypyExprProto(line=line, col=col)

        # Attach resolved type if available
        try:
            typ = self.types.get(expr)
            if typ is not None:
                proto.expr_type.CopyFrom(self.type_mapper.map(typ))
        except Exception:
            pass

        if isinstance(expr, IntExpr):
            int_proto = pir_pb2.MypyIntExprProto()
            if -(2**63) <= expr.value <= 2**63 - 1:
                int_proto.value = expr.value
            else:
                int_proto.str_value = str(expr.value)
            proto.int_expr.CopyFrom(int_proto)
        elif isinstance(expr, StrExpr):
            proto.str_expr.CopyFrom(pir_pb2.MypyStrExprProto(value=expr.value))
        elif isinstance(expr, FloatExpr):
            proto.float_expr.CopyFrom(pir_pb2.MypyFloatExprProto(value=expr.value))
        elif isinstance(expr, BytesExpr):
            proto.bytes_expr.CopyFrom(
                pir_pb2.MypyBytesExprProto(
                    value=expr.value.encode("latin-1", errors="replace")
                )
            )
        elif isinstance(expr, ComplexExpr):
            proto.complex_expr.CopyFrom(
                pir_pb2.MypyComplexExprProto(real=expr.value.real, imag=expr.value.imag)
            )
        elif isinstance(expr, EllipsisExpr):
            proto.ellipsis_expr.CopyFrom(pir_pb2.MypyEllipsisExprProto())
        elif isinstance(expr, NameExpr):
            name_proto = pir_pb2.MypyNameExprProto(name=expr.name)
            fullname = ""
            if expr.node is not None:
                fullname = getattr(expr.node, "fullname", "") or ""
            name_proto.fullname = fullname
            # Determine kind
            if expr.name in ("True", "False", "None"):
                name_proto.name_kind = pir_pb2.NAME_BUILTIN
            elif fullname and "." in fullname:
                name_proto.name_kind = pir_pb2.NAME_GLOBAL
            else:
                name_proto.name_kind = pir_pb2.NAME_LOCAL
            proto.name_expr.CopyFrom(name_proto)
        elif isinstance(expr, MemberExpr):
            member_proto = pir_pb2.MypyMemberExprProto(
                expr=self._serialize_expr(expr.expr),
                name=expr.name,
            )
            if expr.node is not None and hasattr(expr.node, "fullname"):
                member_proto.fullname = expr.node.fullname or ""
            proto.member_expr.CopyFrom(member_proto)
        elif isinstance(expr, CallExpr):
            call_proto = pir_pb2.MypyCallExprProto(
                callee=self._serialize_expr(expr.callee),
            )
            # Resolved callee
            if hasattr(expr.callee, "node"):
                node = expr.callee.node
                if node is not None and hasattr(node, "fullname"):
                    call_proto.resolved_callee = node.fullname or ""
            # Args
            for i, arg_expr in enumerate(expr.args):
                kind = int(expr.arg_kinds[i].value)
                name = expr.arg_names[i] if expr.arg_names else None
                call_proto.args.append(
                    pir_pb2.MypyCallArgProto(
                        expr=self._serialize_expr(arg_expr),
                        kind=kind,
                        name=name or "",
                    )
                )
            proto.call_expr.CopyFrom(call_proto)
        elif isinstance(expr, OpExpr):
            proto.op_expr.CopyFrom(
                pir_pb2.MypyOpExprProto(
                    op=expr.op,
                    left=self._serialize_expr(expr.left),
                    right=self._serialize_expr(expr.right),
                )
            )
        elif isinstance(expr, UnaryExpr):
            proto.unary_expr.CopyFrom(
                pir_pb2.MypyUnaryExprProto(
                    op=expr.op,
                    expr=self._serialize_expr(expr.expr),
                )
            )
        elif isinstance(expr, ComparisonExpr):
            cmp_proto = pir_pb2.MypyComparisonExprProto(
                operators=list(expr.operators),
            )
            for operand in expr.operands:
                cmp_proto.operands.append(self._serialize_expr(operand))
            proto.comparison_expr.CopyFrom(cmp_proto)
        elif isinstance(expr, IndexExpr):
            proto.index_expr.CopyFrom(
                pir_pb2.MypyIndexExprProto(
                    base=self._serialize_expr(expr.base),
                    index=self._serialize_expr(expr.index),
                )
            )
        elif isinstance(expr, SliceExpr):
            slice_proto = pir_pb2.MypySliceExprProto()
            if expr.begin_index:
                slice_proto.begin.CopyFrom(self._serialize_expr(expr.begin_index))
            if expr.end_index:
                slice_proto.end.CopyFrom(self._serialize_expr(expr.end_index))
            if expr.stride:
                slice_proto.stride.CopyFrom(self._serialize_expr(expr.stride))
            proto.slice_expr.CopyFrom(slice_proto)
        elif isinstance(expr, ListExpr):
            list_proto = pir_pb2.MypyListExprProto()
            for item in expr.items:
                list_proto.items.append(self._serialize_expr(item))
            proto.list_expr.CopyFrom(list_proto)
        elif isinstance(expr, TupleExpr):
            tuple_proto = pir_pb2.MypyTupleExprProto()
            for item in expr.items:
                tuple_proto.items.append(self._serialize_expr(item))
            proto.tuple_expr.CopyFrom(tuple_proto)
        elif isinstance(expr, SetExpr):
            set_proto = pir_pb2.MypySetExprProto()
            for item in expr.items:
                set_proto.items.append(self._serialize_expr(item))
            proto.set_expr.CopyFrom(set_proto)
        elif isinstance(expr, DictExpr):
            dict_proto = pir_pb2.MypyDictExprProto()
            for k, v in expr.items:
                if k is not None:
                    dict_proto.keys.append(self._serialize_expr(k))
                else:
                    dict_proto.keys.append(pir_pb2.MypyExprProto())
                dict_proto.values.append(self._serialize_expr(v))
            proto.dict_expr.CopyFrom(dict_proto)
        elif isinstance(expr, ConditionalExpr):
            proto.conditional_expr.CopyFrom(
                pir_pb2.MypyConditionalExprProto(
                    cond=self._serialize_expr(expr.cond),
                    if_expr=self._serialize_expr(expr.if_expr),
                    else_expr=self._serialize_expr(expr.else_expr),
                )
            )
        elif isinstance(expr, StarExpr):
            proto.star_expr.CopyFrom(
                pir_pb2.MypyStarExprProto(expr=self._serialize_expr(expr.expr))
            )
        elif isinstance(expr, YieldExpr):
            yield_proto = pir_pb2.MypyYieldExprProto()
            if expr.expr:
                yield_proto.expr.CopyFrom(self._serialize_expr(expr.expr))
            proto.yield_expr.CopyFrom(yield_proto)
        elif isinstance(expr, YieldFromExpr):
            proto.yield_from_expr.CopyFrom(
                pir_pb2.MypyYieldFromExprProto(expr=self._serialize_expr(expr.expr))
            )
        elif isinstance(expr, AwaitExpr):
            proto.await_expr.CopyFrom(
                pir_pb2.MypyAwaitExprProto(expr=self._serialize_expr(expr.expr))
            )
        elif isinstance(expr, AssignmentExpr):
            proto.assignment_expr.CopyFrom(
                pir_pb2.MypyAssignmentExprProto(
                    target=self._serialize_expr(expr.target),
                    value=self._serialize_expr(expr.value),
                )
            )
        elif isinstance(expr, LambdaExpr):
            lambda_proto = pir_pb2.MypyLambdaExprProto()
            for arg in expr.arguments:
                lambda_proto.arguments.append(self._serialize_argument(arg))
            if expr.body:
                lambda_proto.body.CopyFrom(self._serialize_block(expr.body))
            func_type = expr.type
            if isinstance(func_type, CallableType):
                lambda_proto.return_type.CopyFrom(
                    self.type_mapper.map(func_type.ret_type)
                )
            proto.lambda_expr.CopyFrom(lambda_proto)
        elif isinstance(expr, SuperExpr):
            proto.super_expr.CopyFrom(pir_pb2.MypySuperExprProto())
        elif isinstance(expr, ListComprehension):
            proto.list_comprehension.CopyFrom(
                pir_pb2.MypyListComprehensionProto(
                    generator=self._serialize_generator_expr(expr.generator)
                )
            )
        elif isinstance(expr, SetComprehension):
            proto.set_comprehension.CopyFrom(
                pir_pb2.MypySetComprehensionProto(
                    generator=self._serialize_generator_expr(expr.generator)
                )
            )
        elif isinstance(expr, DictionaryComprehension):
            dict_comp = pir_pb2.MypyDictComprehensionProto(
                key=self._serialize_expr(expr.key),
                value=self._serialize_expr(expr.value),
            )
            for idx in expr.indices:
                dict_comp.indices.append(self._serialize_expr(idx))
            for seq in expr.sequences:
                dict_comp.sequences.append(self._serialize_expr(seq))
            for conds in expr.condlists:
                cl = pir_pb2.MypyCondListProto()
                for c in conds:
                    cl.conditions.append(self._serialize_expr(c))
                dict_comp.condlists.append(cl)
            proto.dict_comprehension.CopyFrom(dict_comp)
        elif isinstance(expr, GeneratorExpr):
            proto.generator_expr.CopyFrom(self._serialize_generator_expr(expr))
        # else: leave kind unset (will be NAME_LOCAL/empty on Kotlin side)

        return proto

    def _serialize_generator_expr(
        self, gen: GeneratorExpr
    ) -> pir_pb2.MypyGeneratorExprProto:
        proto = pir_pb2.MypyGeneratorExprProto(
            left_expr=self._serialize_expr(gen.left_expr),
        )
        for idx in gen.indices:
            proto.indices.append(self._serialize_expr(idx))
        for seq in gen.sequences:
            proto.sequences.append(self._serialize_expr(seq))
        for conds in gen.condlists:
            cl = pir_pb2.MypyCondListProto()
            for c in conds:
                cl.conditions.append(self._serialize_expr(c))
            proto.condlists.append(cl)
        return proto

    # ─── Helpers ──────────────────────────────────────────

    def _unwrap_func(self, defn) -> FuncDef | None:
        if isinstance(defn, FuncDef):
            return defn
        elif isinstance(defn, Decorator):
            return defn.func
        elif isinstance(defn, OverloadedFuncDef):
            if defn.items:
                return self._unwrap_func(defn.items[0])
        return None

    def _collect_free_vars(
        self, func_def: FuncDef, sibling_func_names: set[str] | None = None
    ) -> list[str]:
        """Collect free variable names referenced in a nested function body.

        Free variables are names used in the function body that are NOT:
        - function parameters
        - assigned locally (simple assignment targets)
        - global/nonlocal declarations
        - builtin names
        - sibling nested function names (they're extracted separately)
        """
        param_names = set()
        for arg in func_def.arguments:
            if arg.variable:
                param_names.add(arg.variable.name)

        local_defs = set()
        referenced = set()
        nonlocal_names = set()

        def _scan_expr(expr):
            """Recursively collect NameExpr references from an expression."""
            if expr is None:
                return
            if isinstance(expr, NameExpr):
                referenced.add(expr.name)
            elif isinstance(expr, MemberExpr):
                _scan_expr(expr.expr)
            elif isinstance(expr, (OpExpr, ComparisonExpr)):
                if isinstance(expr, OpExpr):
                    _scan_expr(expr.left)
                    _scan_expr(expr.right)
                elif isinstance(expr, ComparisonExpr):
                    for op in expr.operands:
                        _scan_expr(op)
            elif isinstance(expr, UnaryExpr):
                _scan_expr(expr.expr)
            elif isinstance(expr, CallExpr):
                _scan_expr(expr.callee)
                for a in expr.args:
                    _scan_expr(a)
            elif isinstance(expr, IndexExpr):
                _scan_expr(expr.base)
                _scan_expr(expr.index)
            elif isinstance(expr, (ListExpr, TupleExpr, SetExpr)):
                for item in expr.items:
                    _scan_expr(item)
            elif isinstance(expr, DictExpr):
                for k, v in zip(expr.keys, expr.values):
                    _scan_expr(k)
                    _scan_expr(v)
            elif isinstance(expr, ConditionalExpr):
                _scan_expr(expr.cond)
                _scan_expr(expr.if_expr)
                _scan_expr(expr.else_expr)
            elif isinstance(expr, StarExpr):
                _scan_expr(expr.expr)
            elif isinstance(expr, AssignmentExpr):
                _scan_expr(expr.target)
                _scan_expr(expr.value)
            elif isinstance(expr, SliceExpr):
                _scan_expr(expr.begin_index)
                _scan_expr(expr.end_index)
                _scan_expr(expr.stride)
            elif isinstance(expr, YieldExpr):
                _scan_expr(expr.expr)
            elif isinstance(expr, YieldFromExpr):
                _scan_expr(expr.expr)
            elif isinstance(expr, AwaitExpr):
                _scan_expr(expr.expr)
            elif isinstance(expr, LambdaExpr):
                # Lambda captures its own scope; skip
                pass
            elif isinstance(
                expr,
                (
                    ListComprehension,
                    SetComprehension,
                    DictionaryComprehension,
                    GeneratorExpr,
                ),
            ):
                # Comprehensions have their own scope; skip
                pass

        def _scan_stmt(stmt):
            """Recursively collect local defs and referenced names from statements."""
            if isinstance(stmt, AssignmentStmt):
                for lv in stmt.lvalues:
                    if isinstance(lv, NameExpr):
                        local_defs.add(lv.name)
                    elif isinstance(lv, (TupleExpr, ListExpr)):
                        for item in lv.items:
                            if isinstance(item, NameExpr):
                                local_defs.add(item.name)
                _scan_expr(stmt.rvalue)
            elif isinstance(stmt, OperatorAssignmentStmt):
                if isinstance(stmt.lvalue, NameExpr):
                    local_defs.add(stmt.lvalue.name)
                _scan_expr(stmt.rvalue)
            elif isinstance(stmt, (FuncDef, Decorator, OverloadedFuncDef)):
                # Nested function inside nested function — skip
                pass
            elif isinstance(stmt, ReturnStmt):
                _scan_expr(stmt.expr)
            elif isinstance(stmt, ExpressionStmt):
                _scan_expr(stmt.expr)
            elif isinstance(stmt, IfStmt):
                for e in stmt.expr:
                    _scan_expr(e)
                for b in stmt.body:
                    _scan_block(b)
                if stmt.else_body:
                    _scan_block(stmt.else_body)
            elif isinstance(stmt, WhileStmt):
                _scan_expr(stmt.expr)
                _scan_block(stmt.body)
                if stmt.else_body:
                    _scan_block(stmt.else_body)
            elif isinstance(stmt, ForStmt):
                if isinstance(stmt.index, NameExpr):
                    local_defs.add(stmt.index.name)
                _scan_expr(stmt.expr)
                _scan_block(stmt.body)
                if stmt.else_body:
                    _scan_block(stmt.else_body)
            elif isinstance(stmt, WithStmt):
                for target in stmt.target:
                    if target and isinstance(target, NameExpr):
                        local_defs.add(target.name)
                for expr_node in stmt.expr:
                    _scan_expr(expr_node)
                _scan_block(stmt.body)
            elif isinstance(stmt, TryStmt):
                _scan_block(stmt.body)
                for handler in stmt.handlers:
                    _scan_block(handler)
                if stmt.else_body:
                    _scan_block(stmt.else_body)
                if stmt.finally_body:
                    _scan_block(stmt.finally_body)
                for v in stmt.vars:
                    if v and isinstance(v, NameExpr):
                        local_defs.add(v.name)
            elif isinstance(stmt, RaiseStmt):
                _scan_expr(stmt.expr)
                _scan_expr(stmt.from_expr)
            elif isinstance(stmt, DelStmt):
                _scan_expr(stmt.expr)
            elif isinstance(stmt, AssertStmt):
                _scan_expr(stmt.expr)
                _scan_expr(stmt.msg)
            elif isinstance(stmt, GlobalDecl):
                pass  # global names are not free vars
            elif isinstance(stmt, NonlocalDecl):
                nonlocal_names.update(stmt.names)
            # PassStmt, BreakStmt, ContinueStmt — no-op

        def _scan_block(block):
            if block and hasattr(block, "body"):
                for s in block.body:
                    _scan_stmt(s)

        _scan_block(func_def.body)

        # Free vars = referenced - params - locally defined
        free = referenced - param_names - local_defs
        # Remove common builtins
        builtins_names = {
            "True",
            "False",
            "None",
            "print",
            "len",
            "range",
            "int",
            "str",
            "float",
            "bool",
            "list",
            "dict",
            "set",
            "tuple",
            "type",
            "super",
            "isinstance",
            "issubclass",
            "hasattr",
            "getattr",
            "setattr",
            "delattr",
            "callable",
            "iter",
            "next",
            "enumerate",
            "zip",
            "map",
            "filter",
            "sorted",
            "reversed",
            "sum",
            "min",
            "max",
            "abs",
            "all",
            "any",
            "repr",
            "hash",
            "id",
            "input",
            "open",
            "chr",
            "ord",
            "hex",
            "oct",
            "bin",
            "format",
            "object",
            "property",
            "staticmethod",
            "classmethod",
            "ValueError",
            "TypeError",
            "RuntimeError",
            "KeyError",
            "IndexError",
            "AttributeError",
            "StopIteration",
            "Exception",
            "BaseException",
            "NotImplementedError",
            "AssertionError",
            "OSError",
            "IOError",
            "FileNotFoundError",
            "PermissionError",
        }
        free -= builtins_names
        # Add nonlocal names back (they ARE captured)
        free |= nonlocal_names
        return sorted(free)

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
