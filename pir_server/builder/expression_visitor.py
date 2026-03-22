"""ExpressionTransformer: accepts a mypy Expression, emits instructions,
returns a PIRValueProto representing the result."""

from __future__ import annotations
from typing import TYPE_CHECKING
from mypy.nodes import (
    IntExpr,
    StrExpr,
    FloatExpr,
    BytesExpr,
    ComplexExpr,
    NameExpr,
    MemberExpr,
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
    EllipsisExpr,
    SuperExpr,
    ListComprehension,
    SetComprehension,
    DictionaryComprehension,
    GeneratorExpr,
    AssignmentExpr,
    LambdaExpr,
    Expression,
    ARG_POS,
    ARG_STAR,
    ARG_STAR2,
    ARG_NAMED,
    ARG_NAMED_OPT,
)
from pir_server.proto import pir_pb2

if TYPE_CHECKING:
    from pir_server.builder.statement_visitor import StatementTransformer


# Mapping of Python operator strings to proto BinaryOperator enum values
_BIN_OP_MAP = {
    "+": pir_pb2.ADD,
    "-": pir_pb2.SUB,
    "*": pir_pb2.MUL,
    "/": pir_pb2.DIV,
    "//": pir_pb2.FLOOR_DIV,
    "%": pir_pb2.MOD,
    "**": pir_pb2.POW,
    "@": pir_pb2.MAT_MUL,
    "&": pir_pb2.BIT_AND,
    "|": pir_pb2.BIT_OR,
    "^": pir_pb2.BIT_XOR,
    "<<": pir_pb2.LSHIFT,
    ">>": pir_pb2.RSHIFT,
}

_UNARY_OP_MAP = {
    "-": pir_pb2.NEG,
    "+": pir_pb2.POS,
    "not": pir_pb2.NOT,
    "~": pir_pb2.INVERT,
}

_COMPARE_OP_MAP = {
    "==": pir_pb2.EQ,
    "!=": pir_pb2.NE,
    "<": pir_pb2.LT,
    "<=": pir_pb2.LE,
    ">": pir_pb2.GT,
    ">=": pir_pb2.GE,
    "is": pir_pb2.IS,
    "is not": pir_pb2.IS_NOT,
    "in": pir_pb2.IN,
    "not in": pir_pb2.NOT_IN,
}


class ExpressionTransformer:
    """
    Each visit method:
      1. Recursively lowers sub-expressions
      2. Emits zero or more PIR instructions into the current block
      3. Returns a PIRValueProto holding the result
    """

    def __init__(self, stmt_transformer: StatementTransformer):
        self.st = stmt_transformer

    def accept(self, expr: Expression) -> pir_pb2.PIRValueProto:
        """Main dispatch — returns the value holding this expression's result."""
        if isinstance(expr, IntExpr):
            return self._const_int(expr.value)
        elif isinstance(expr, StrExpr):
            return self._const_str(expr.value)
        elif isinstance(expr, FloatExpr):
            return self._const_float(expr.value)
        elif isinstance(expr, BytesExpr):
            return self._const_bytes(expr.value)
        elif isinstance(expr, ComplexExpr):
            return self._const_complex(expr.value)
        elif isinstance(expr, EllipsisExpr):
            return self._const_ellipsis()
        elif isinstance(expr, NameExpr):
            return self._visit_name(expr)
        elif isinstance(expr, MemberExpr):
            return self._visit_member(expr)
        elif isinstance(expr, CallExpr):
            return self._visit_call(expr)
        elif isinstance(expr, OpExpr):
            return self._visit_op(expr)
        elif isinstance(expr, UnaryExpr):
            return self._visit_unary(expr)
        elif isinstance(expr, ComparisonExpr):
            return self._visit_comparison(expr)
        elif isinstance(expr, IndexExpr):
            return self._visit_index(expr)
        elif isinstance(expr, SliceExpr):
            return self._visit_slice(expr)
        elif isinstance(expr, ListExpr):
            return self._visit_list(expr)
        elif isinstance(expr, TupleExpr):
            return self._visit_tuple(expr)
        elif isinstance(expr, SetExpr):
            return self._visit_set(expr)
        elif isinstance(expr, DictExpr):
            return self._visit_dict(expr)
        elif isinstance(expr, ConditionalExpr):
            return self._visit_conditional(expr)
        elif isinstance(expr, StarExpr):
            return self.accept(expr.expr)
        elif isinstance(expr, YieldExpr):
            return self._visit_yield(expr)
        elif isinstance(expr, YieldFromExpr):
            return self._visit_yield_from(expr)
        elif isinstance(expr, AwaitExpr):
            return self._visit_await(expr)
        elif isinstance(expr, AssignmentExpr):
            return self._visit_walrus(expr)
        elif isinstance(expr, ListComprehension):
            return self._visit_list_comprehension(expr)
        elif isinstance(expr, SetComprehension):
            return self._visit_set_comprehension(expr)
        elif isinstance(expr, DictionaryComprehension):
            return self._visit_dict_comprehension(expr)
        elif isinstance(expr, GeneratorExpr):
            return self._visit_generator_expr(expr)
        elif isinstance(expr, LambdaExpr):
            return self._visit_lambda(expr)
        elif isinstance(expr, SuperExpr):
            return self._visit_super(expr)
        else:
            return self._const_none()

    # ─── Constants ──────────────────────────────────────────

    def _const_int(self, value: int) -> pir_pb2.PIRValueProto:
        # Protobuf int64 range: clamp Python's arbitrary-precision ints
        if -(2**63) <= value <= 2**63 - 1:
            return pir_pb2.PIRValueProto(
                const_val=pir_pb2.PIRConstProto(int_value=value)
            )
        else:
            # Fall back to string representation for huge ints
            return pir_pb2.PIRValueProto(
                const_val=pir_pb2.PIRConstProto(string_value=str(value))
            )

    def _const_float(self, value: float) -> pir_pb2.PIRValueProto:
        return pir_pb2.PIRValueProto(const_val=pir_pb2.PIRConstProto(float_value=value))

    def _const_str(self, value: str) -> pir_pb2.PIRValueProto:
        return pir_pb2.PIRValueProto(
            const_val=pir_pb2.PIRConstProto(string_value=value)
        )

    def _const_bytes(self, value: str) -> pir_pb2.PIRValueProto:
        return pir_pb2.PIRValueProto(
            const_val=pir_pb2.PIRConstProto(
                bytes_value=value.encode("latin-1", errors="replace")
            )
        )

    def _const_complex(self, value: complex) -> pir_pb2.PIRValueProto:
        return pir_pb2.PIRValueProto(
            const_val=pir_pb2.PIRConstProto(
                complex_real=value.real, complex_imag=value.imag
            )
        )

    def _const_bool(self, value: bool) -> pir_pb2.PIRValueProto:
        return pir_pb2.PIRValueProto(const_val=pir_pb2.PIRConstProto(bool_value=value))

    def _const_none(self) -> pir_pb2.PIRValueProto:
        return pir_pb2.PIRValueProto(const_val=pir_pb2.PIRConstProto(none_value=True))

    def _const_ellipsis(self) -> pir_pb2.PIRValueProto:
        return pir_pb2.PIRValueProto(
            const_val=pir_pb2.PIRConstProto(ellipsis_value=True)
        )

    # ─── Names & Attributes ────────────────────────────────

    def _visit_name(self, expr: NameExpr) -> pir_pb2.PIRValueProto:
        name = expr.name
        # Check if it's a global/imported reference
        if expr.node is not None:
            fullname = getattr(expr.node, "fullname", "")
            if fullname and "." in fullname:
                module = fullname.rsplit(".", 1)[0]
                return pir_pb2.PIRValueProto(
                    global_ref=pir_pb2.PIRGlobalRefProto(name=name, module=module)
                )
        # Check for known builtins
        if name in ("True", "False"):
            return self._const_bool(name == "True")
        if name == "None":
            return self._const_none()
        # Local or parameter
        resolved = self.st.scope.resolve_local(name)
        return pir_pb2.PIRValueProto(local=pir_pb2.PIRLocalProto(name=resolved))

    def _visit_member(self, expr: MemberExpr) -> pir_pb2.PIRValueProto:
        obj = self.accept(expr.expr)
        target = self.st._new_temp_value()
        line = expr.line if hasattr(expr, "line") else -1
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                load_attr=pir_pb2.PIRLoadAttrProto(
                    target=target,
                    object=obj,
                    attribute=expr.name,
                ),
            )
        )
        return target

    # ─── Operators ──────────────────────────────────────────

    def _visit_op(self, expr: OpExpr) -> pir_pb2.PIRValueProto:
        # Handle augmented assignment operators (+=, etc)
        op_str = expr.op
        if op_str in _BIN_OP_MAP:
            left = self.accept(expr.left)
            right = self.accept(expr.right)
            target = self.st._new_temp_value()
            self.st._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(expr, "line", -1),
                    bin_op=pir_pb2.PIRBinOpProto(
                        target=target,
                        left=left,
                        right=right,
                        op=_BIN_OP_MAP[op_str],
                    ),
                )
            )
            return target
        elif op_str == "and":
            return self._visit_short_circuit(expr, is_and=True)
        elif op_str == "or":
            return self._visit_short_circuit(expr, is_and=False)
        else:
            # Fallback
            return self._const_none()

    def _visit_short_circuit(self, expr: OpExpr, is_and: bool) -> pir_pb2.PIRValueProto:
        """Lower `a and b` / `a or b` with short-circuit evaluation."""
        left = self.accept(expr.left)
        target = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                assign=pir_pb2.PIRAssignProto(target=target, source=left),
            )
        )

        eval_right = self.st._new_block()
        end_block = self.st._new_block()

        if is_and:
            self.st._emit_branch(
                target, eval_right, end_block, getattr(expr, "line", -1)
            )
        else:
            self.st._emit_branch(
                target, end_block, eval_right, getattr(expr, "line", -1)
            )

        self.st._activate(eval_right)
        right = self.accept(expr.right)
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                assign=pir_pb2.PIRAssignProto(target=target, source=right),
            )
        )
        self.st._emit_goto(end_block, getattr(expr, "line", -1))
        self.st._activate(end_block)
        return target

    def _visit_unary(self, expr: UnaryExpr) -> pir_pb2.PIRValueProto:
        operand = self.accept(expr.expr)
        target = self.st._new_temp_value()
        op = _UNARY_OP_MAP.get(expr.op, pir_pb2.NEG)
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                unary_op=pir_pb2.PIRUnaryOpProto(
                    target=target,
                    operand=operand,
                    op=op,
                ),
            )
        )
        return target

    def _visit_comparison(self, expr: ComparisonExpr) -> pir_pb2.PIRValueProto:
        """Lower chained comparisons: a < b < c → (a < b) and (b < c)"""
        if len(expr.operators) == 1:
            left = self.accept(expr.operands[0])
            right = self.accept(expr.operands[1])
            target = self.st._new_temp_value()
            op = _COMPARE_OP_MAP.get(expr.operators[0], pir_pb2.EQ)
            self.st._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(expr, "line", -1),
                    compare=pir_pb2.PIRCompareProto(
                        target=target,
                        left=left,
                        right=right,
                        op=op,
                    ),
                )
            )
            return target
        else:
            # Chained: lower to (a op1 b) and (b op2 c) and ...
            result = None
            prev_right = self.accept(expr.operands[0])
            for i, op_str in enumerate(expr.operators):
                next_right = self.accept(expr.operands[i + 1])
                cmp_target = self.st._new_temp_value()
                op = _COMPARE_OP_MAP.get(op_str, pir_pb2.EQ)
                self.st._emit(
                    pir_pb2.PIRInstructionProto(
                        line_number=getattr(expr, "line", -1),
                        compare=pir_pb2.PIRCompareProto(
                            target=cmp_target,
                            left=prev_right,
                            right=next_right,
                            op=op,
                        ),
                    )
                )
                if result is None:
                    result = cmp_target
                else:
                    and_target = self.st._new_temp_value()
                    self.st._emit(
                        pir_pb2.PIRInstructionProto(
                            line_number=getattr(expr, "line", -1),
                            bin_op=pir_pb2.PIRBinOpProto(
                                target=and_target,
                                left=result,
                                right=cmp_target,
                                op=pir_pb2.BIT_AND,
                            ),
                        )
                    )
                    result = and_target
                prev_right = next_right
            return result

    # ─── Call ──────────────────────────────────────────────

    def _visit_call(self, expr: CallExpr) -> pir_pb2.PIRValueProto:
        callee = self.accept(expr.callee)
        args = []
        for i, arg_expr in enumerate(expr.args):
            arg_val = self.accept(arg_expr)
            kind = expr.arg_kinds[i]
            name = expr.arg_names[i] if expr.arg_names else None

            if kind in (ARG_STAR,):
                proto_kind = pir_pb2.STAR
            elif kind in (ARG_STAR2,):
                proto_kind = pir_pb2.DOUBLE_STAR
            elif kind in (ARG_NAMED, ARG_NAMED_OPT):
                proto_kind = pir_pb2.KEYWORD
            else:
                proto_kind = pir_pb2.POSITIONAL

            args.append(
                pir_pb2.PIRCallArgProto(
                    value=arg_val,
                    kind=proto_kind,
                    keyword=name or "",
                )
            )

        target = self.st._new_temp_value()
        resolved = ""
        if hasattr(expr, "callee") and hasattr(expr.callee, "node"):
            node = expr.callee.node
            if node is not None and hasattr(node, "fullname"):
                resolved = node.fullname or ""

        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                call=pir_pb2.PIRCallProto(
                    target=target,
                    callee=callee,
                    args=args,
                    resolved_callee=resolved,
                ),
            )
        )
        return target

    # ─── Subscript & Slice ────────────────────────────────

    def _visit_index(self, expr: IndexExpr) -> pir_pb2.PIRValueProto:
        obj = self.accept(expr.base)
        index = self.accept(expr.index)
        target = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                load_subscript=pir_pb2.PIRLoadSubscriptProto(
                    target=target,
                    object=obj,
                    index=index,
                ),
            )
        )
        return target

    def _visit_slice(self, expr: SliceExpr) -> pir_pb2.PIRValueProto:
        target = self.st._new_temp_value()
        lower = self.accept(expr.begin_index) if expr.begin_index else None
        upper = self.accept(expr.end_index) if expr.end_index else None
        step = self.accept(expr.stride) if expr.stride else None
        build_slice = pir_pb2.PIRBuildSliceProto(target=target)
        if lower:
            build_slice.lower.CopyFrom(lower)
        if upper:
            build_slice.upper.CopyFrom(upper)
        if step:
            build_slice.step.CopyFrom(step)
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                build_slice=build_slice,
            )
        )
        return target

    # ─── Collection literals ───────────────────────────────

    def _visit_list(self, expr: ListExpr) -> pir_pb2.PIRValueProto:
        elements = [self.accept(e) for e in expr.items]
        target = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                build_list=pir_pb2.PIRBuildListProto(target=target, elements=elements),
            )
        )
        return target

    def _visit_tuple(self, expr: TupleExpr) -> pir_pb2.PIRValueProto:
        elements = [self.accept(e) for e in expr.items]
        target = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                build_tuple=pir_pb2.PIRBuildTupleProto(
                    target=target, elements=elements
                ),
            )
        )
        return target

    def _visit_set(self, expr: SetExpr) -> pir_pb2.PIRValueProto:
        elements = [self.accept(e) for e in expr.items]
        target = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                build_set=pir_pb2.PIRBuildSetProto(target=target, elements=elements),
            )
        )
        return target

    def _visit_dict(self, expr: DictExpr) -> pir_pb2.PIRValueProto:
        keys = [self.accept(k) if k else self._const_none() for k, v in expr.items]
        values = [self.accept(v) for k, v in expr.items]
        target = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                build_dict=pir_pb2.PIRBuildDictProto(
                    target=target, keys=keys, values=values
                ),
            )
        )
        return target

    # ─── Conditional expression ──────────────────────────

    def _visit_conditional(self, expr: ConditionalExpr) -> pir_pb2.PIRValueProto:
        """x if cond else y"""
        cond = self.accept(expr.cond)
        target = self.st._new_temp_value()
        true_block = self.st._new_block()
        false_block = self.st._new_block()
        end_block = self.st._new_block()

        self.st._emit_branch(cond, true_block, false_block, getattr(expr, "line", -1))

        self.st._activate(true_block)
        true_val = self.accept(expr.if_expr)
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                assign=pir_pb2.PIRAssignProto(target=target, source=true_val),
            )
        )
        self.st._emit_goto(end_block, -1)

        self.st._activate(false_block)
        false_val = self.accept(expr.else_expr)
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                assign=pir_pb2.PIRAssignProto(target=target, source=false_val),
            )
        )
        self.st._emit_goto(end_block, -1)

        self.st._activate(end_block)
        return target

    # ─── Generators & Async ────────────────────────────────

    def _visit_yield(self, expr: YieldExpr) -> pir_pb2.PIRValueProto:
        value = self.accept(expr.expr) if expr.expr else self._const_none()
        target = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                yield_inst=pir_pb2.PIRYieldProto(target=target, value=value),
            )
        )
        return target

    def _visit_yield_from(self, expr: YieldFromExpr) -> pir_pb2.PIRValueProto:
        iterable = self.accept(expr.expr)
        target = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                yield_from=pir_pb2.PIRYieldFromProto(target=target, iterable=iterable),
            )
        )
        return target

    def _visit_await(self, expr: AwaitExpr) -> pir_pb2.PIRValueProto:
        awaitable = self.accept(expr.expr)
        target = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                await_inst=pir_pb2.PIRAwaitProto(target=target, awaitable=awaitable),
            )
        )
        return target

    def _visit_walrus(self, expr: AssignmentExpr) -> pir_pb2.PIRValueProto:
        """x := value"""
        value = self.accept(expr.value)
        target_name = (
            expr.target.name
            if hasattr(expr.target, "name")
            else self.st.scope.new_temp()
        )
        target = pir_pb2.PIRValueProto(
            local=pir_pb2.PIRLocalProto(name=self.st.scope.resolve_local(target_name))
        )
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                assign=pir_pb2.PIRAssignProto(target=target, source=value),
            )
        )
        return target

    # ─── Lambda ─────────────────────────────────────────────

    def _visit_lambda(self, expr: LambdaExpr) -> pir_pb2.PIRValueProto:
        """Lower a lambda expression to a synthetic function.

        Creates a PIRFunctionProto with the lambda's CFG and registers it
        in the module builder's lambda_functions list. Returns a global
        reference to the synthesized function name.
        """
        from pir_server.builder.statement_visitor import StatementTransformer
        from pir_server.builder.scope import ScopeStack
        from mypy.types import CallableType

        mb = self.st.module_builder
        if mb is None:
            # No module builder available; fall back to None
            return self._const_none()

        # Generate a unique name for the lambda
        idx = mb.lambda_counter
        mb.lambda_counter += 1
        lambda_name = f"<lambda>${idx}"
        qualified_name = f"{mb.module_name}.{lambda_name}"

        # Build the lambda's CFG using a fresh StatementTransformer
        scope = ScopeStack()
        stmt_visitor = StatementTransformer(
            types=self.st.types,
            type_mapper=self.st.type_mapper,
            scope=scope,
            module_builder=mb,
        )
        try:
            cfg_proto = stmt_visitor.build_function_cfg(expr)
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

        # Build the function proto
        func_proto = pir_pb2.PIRFunctionProto(
            name=lambda_name,
            qualified_name=qualified_name,
            cfg=cfg_proto,
        )

        # Add parameters
        from mypy.nodes import (
            ARG_POS,
            ARG_OPT,
            ARG_STAR,
            ARG_STAR2,
            ARG_NAMED,
            ARG_NAMED_OPT,
        )

        kind_map = {
            ARG_POS: pir_pb2.POSITIONAL_OR_KEYWORD,
            ARG_OPT: pir_pb2.POSITIONAL_OR_KEYWORD,
            ARG_STAR: pir_pb2.VAR_POSITIONAL,
            ARG_STAR2: pir_pb2.VAR_KEYWORD,
            ARG_NAMED: pir_pb2.KEYWORD_ONLY,
            ARG_NAMED_OPT: pir_pb2.KEYWORD_ONLY,
        }
        for arg in expr.arguments:
            param = pir_pb2.PIRParameterProto(
                name=arg.variable.name if arg.variable else "",
                kind=kind_map.get(arg.kind, pir_pb2.POSITIONAL_OR_KEYWORD),
                has_default=arg.initializer is not None,
            )
            if arg.variable and arg.variable.type:
                param.type.CopyFrom(mb.type_mapper.map(arg.variable.type))
            func_proto.parameters.append(param)

        # Set return type if available
        func_type = expr.type
        if isinstance(func_type, CallableType):
            func_proto.return_type.CopyFrom(mb.type_mapper.map(func_type.ret_type))

        # Register the synthetic function
        mb.lambda_functions.append(func_proto)

        # Return a global reference to the lambda
        return pir_pb2.PIRValueProto(
            global_ref=pir_pb2.PIRGlobalRefProto(
                name=lambda_name,
                module=mb.module_name,
            )
        )

    # ─── Comprehensions ────────────────────────────────────

    def _visit_list_comprehension(
        self, expr: ListComprehension
    ) -> pir_pb2.PIRValueProto:
        """Lower [expr for x in iterable if cond] to a loop that builds a list."""
        return self._visit_comprehension_as_loop(
            expr.generator, "list", getattr(expr, "line", -1)
        )

    def _visit_set_comprehension(self, expr: SetComprehension) -> pir_pb2.PIRValueProto:
        """Lower {expr for x in iterable if cond} to a loop that builds a set."""
        return self._visit_comprehension_as_loop(
            expr.generator, "set", getattr(expr, "line", -1)
        )

    def _visit_dict_comprehension(
        self, expr: DictionaryComprehension
    ) -> pir_pb2.PIRValueProto:
        """Lower {k: v for x in iterable if cond} to a loop that builds a dict."""
        line = getattr(expr, "line", -1)
        result = self.st._new_temp_value()

        # result = {}
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                build_dict=pir_pb2.PIRBuildDictProto(target=result, keys=[], values=[]),
            )
        )

        # For each loop level (dict comprehensions can have nested loops)
        self._emit_comprehension_loops(
            indices=expr.indices,
            sequences=expr.sequences,
            condlists=expr.condlists,
            body_callback=lambda: self._emit_dict_store(
                result, expr.key, expr.value, line
            ),
            line=line,
            loop_idx=0,
        )

        return result

    def _visit_generator_expr(self, expr: GeneratorExpr) -> pir_pb2.PIRValueProto:
        """Lower a generator expression to a loop building a list.

        Generator expressions in non-iteration contexts are materialized as
        lists for IR modeling purposes. For taint analysis this is
        semantically equivalent.
        """
        return self._visit_comprehension_as_loop(
            expr, "list", getattr(expr, "line", -1)
        )

    def _visit_comprehension_as_loop(
        self, gen: GeneratorExpr, collection_type: str, line: int
    ) -> pir_pb2.PIRValueProto:
        """Shared lowering for list/set comprehensions and generator expressions."""
        result = self.st._new_temp_value()

        # result = [] or result = set()
        if collection_type == "list":
            self.st._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=line,
                    build_list=pir_pb2.PIRBuildListProto(target=result, elements=[]),
                )
            )
        else:
            # set() call
            set_ref = pir_pb2.PIRValueProto(
                global_ref=pir_pb2.PIRGlobalRefProto(name="set", module="builtins")
            )
            self.st._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=line,
                    call=pir_pb2.PIRCallProto(target=result, callee=set_ref, args=[]),
                )
            )

        # Emit the nested loop structure
        add_method = "append" if collection_type == "list" else "add"

        self._emit_comprehension_loops(
            indices=gen.indices,
            sequences=gen.sequences,
            condlists=gen.condlists,
            body_callback=lambda: self._emit_collection_add(
                result, gen.left_expr, add_method, line
            ),
            line=line,
            loop_idx=0,
        )

        return result

    def _emit_comprehension_loops(
        self, indices, sequences, condlists, body_callback, line: int, loop_idx: int
    ):
        """Recursively emit nested for-loops with conditions for comprehensions."""
        if loop_idx >= len(sequences):
            # Base case: emit the body
            body_callback()
            return

        # Get iter for this level
        iterable_val = self.accept(sequences[loop_idx])
        iter_val = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                get_iter=pir_pb2.PIRGetIterProto(
                    target=iter_val, iterable=iterable_val
                ),
            )
        )

        header_block = self.st._new_block()
        body_block = self.st._new_block()
        exit_block = self.st._new_block()

        self.st._emit_goto(header_block)
        self.st._activate(header_block)

        # Loop variable
        target_val = self.st._new_temp_value()
        # If the index is a NameExpr, resolve it to a local
        idx_expr = indices[loop_idx]
        from mypy.nodes import NameExpr as NE, TupleExpr as TE

        if isinstance(idx_expr, NE):
            target_val = pir_pb2.PIRValueProto(
                local=pir_pb2.PIRLocalProto(
                    name=self.st.scope.resolve_local(idx_expr.name)
                )
            )

        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                next_iter=pir_pb2.PIRNextIterProto(
                    target=target_val,
                    iterator=iter_val,
                    body_block=body_block,
                    exit_block=exit_block,
                ),
            )
        )

        self.st._activate(body_block)

        # Emit tuple unpacking if loop variable is a tuple
        if isinstance(idx_expr, TE):
            self.st._emit_for_target_unpack(idx_expr, target_val, line)

        # Apply conditions for this loop level
        conditions = condlists[loop_idx] if loop_idx < len(condlists) else []
        if conditions:
            for cond_expr in conditions:
                cond_val = self.accept(cond_expr)
                skip_block = self.st._new_block()
                continue_block = self.st._new_block()
                self.st._emit_branch(cond_val, continue_block, skip_block, line)
                self.st._activate(skip_block)
                self.st._emit_goto(header_block)
                self.st._activate(continue_block)

        # Recurse for inner loops or emit body
        self._emit_comprehension_loops(
            indices, sequences, condlists, body_callback, line, loop_idx + 1
        )

        # Back to header
        if not self.st._current_block_terminated():
            self.st._emit_goto(header_block)

        self.st._activate(exit_block)

    def _emit_collection_add(self, collection, value_expr, method: str, line: int):
        """Emit: collection.method(value)"""
        value = self.accept(value_expr)
        method_ref = self.st._new_temp_value()
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                load_attr=pir_pb2.PIRLoadAttrProto(
                    target=method_ref,
                    object=collection,
                    attribute=method,
                ),
            )
        )
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                call=pir_pb2.PIRCallProto(
                    callee=method_ref,
                    args=[
                        pir_pb2.PIRCallArgProto(value=value, kind=pir_pb2.POSITIONAL)
                    ],
                ),
            )
        )

    def _emit_dict_store(self, dict_val, key_expr, value_expr, line: int):
        """Emit: dict[key] = value"""
        key = self.accept(key_expr)
        value = self.accept(value_expr)
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                store_subscript=pir_pb2.PIRStoreSubscriptProto(
                    object=dict_val,
                    index=key,
                    value=value,
                ),
            )
        )

    def _visit_super(self, expr: SuperExpr) -> pir_pb2.PIRValueProto:
        target = self.st._new_temp_value()
        callee = pir_pb2.PIRValueProto(
            global_ref=pir_pb2.PIRGlobalRefProto(name="super", module="builtins")
        )
        self.st._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(expr, "line", -1),
                call=pir_pb2.PIRCallProto(target=target, callee=callee, args=[]),
            )
        )
        return target
