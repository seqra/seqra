"""StatementTransformer: walks mypy statement AST, emits PIR instructions."""

from __future__ import annotations
from mypy.nodes import (
    Block,
    ExpressionStmt,
    AssignmentStmt,
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
    FuncDef,
    MypyFile,
    NameExpr,
    TupleExpr,
    StarExpr,
    Expression,
    MemberExpr,
    IndexExpr,
    OperatorAssignmentStmt,
)
from pir_server.proto import pir_pb2
from pir_server.builder.expression_visitor import ExpressionTransformer
from pir_server.builder.type_mapper import TypeMapper
from pir_server.builder.scope import ScopeStack


class StatementTransformer:
    """
    Core lowering engine. Maintains:
      - current_block: list of instructions being built
      - blocks: list of all PIRBasicBlockProto for current function
      - block_counter: int for unique block labels
      - break_target / continue_target: block labels for loops
      - exception_handlers: stack of handler block labels
    """

    def __init__(self, types: dict, type_mapper: TypeMapper, scope: ScopeStack):
        self.types = types
        self.type_mapper = type_mapper
        self.scope = scope
        self.expr_transformer = ExpressionTransformer(self)

        self.blocks: list[pir_pb2.PIRBasicBlockProto] = []
        self.current_instructions: list[pir_pb2.PIRInstructionProto] = []
        self.current_label: int = 0
        self.block_counter: int = 0
        self.current_exception_handlers: list[int] = []

        self.break_target: int | None = None
        self.continue_target: int | None = None

    def _reset(self):
        self.blocks = []
        self.current_instructions = []
        self.current_label = 0
        self.block_counter = 0
        self.current_exception_handlers = []
        self.break_target = None
        self.continue_target = None

    # ─── Block management ──────────────────────────────────

    def _new_block(self) -> int:
        """Allocate a new block label."""
        self.block_counter += 1
        return self.block_counter

    def _activate(self, label: int):
        """Finalize current block and switch to a new one."""
        self._finalize_current_block()
        self.current_label = label
        self.current_instructions = []

    def _finalize_current_block(self):
        """Save current instructions as a block."""
        if self.current_instructions or self.current_label == 0:
            block = pir_pb2.PIRBasicBlockProto(
                label=self.current_label,
                instructions=list(self.current_instructions),
                exception_handlers=list(self.current_exception_handlers),
            )
            self.blocks.append(block)

    def _current_block_terminated(self) -> bool:
        if not self.current_instructions:
            return False
        last = self.current_instructions[-1]
        return (
            last.HasField("goto_inst")
            or last.HasField("branch")
            or last.HasField("return_inst")
            or last.HasField("raise_inst")
            or last.HasField("unreachable")
            or last.HasField("next_iter")
        )

    # ─── Instruction emission ──────────────────────────────

    def _emit(self, inst: pir_pb2.PIRInstructionProto):
        self.current_instructions.append(inst)

    def _emit_goto(self, target: int, line: int = -1):
        self._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                goto_inst=pir_pb2.PIRGotoProto(target_block=target),
            )
        )

    def _emit_branch(
        self,
        condition: pir_pb2.PIRValueProto,
        true_block: int,
        false_block: int,
        line: int = -1,
    ):
        self._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                branch=pir_pb2.PIRBranchProto(
                    condition=condition,
                    true_block=true_block,
                    false_block=false_block,
                ),
            )
        )

    def _emit_return(self, value: pir_pb2.PIRValueProto | None, line: int = -1):
        ret = pir_pb2.PIRReturnProto()
        if value:
            ret.value.CopyFrom(value)
        self._emit(
            pir_pb2.PIRInstructionProto(
                line_number=line,
                return_inst=ret,
            )
        )

    def _new_temp_value(self) -> pir_pb2.PIRValueProto:
        name = self.scope.new_temp()
        return pir_pb2.PIRValueProto(local=pir_pb2.PIRLocalProto(name=name))

    def _accept_expr(self, expr: Expression) -> pir_pb2.PIRValueProto:
        return self.expr_transformer.accept(expr)

    # ─── CFG builders ─────────────────────────────────────

    def build_function_cfg(self, func_def: FuncDef) -> pir_pb2.PIRCFGProto:
        self._reset()
        entry = 0
        self.current_label = entry
        self.current_instructions = []

        self._visit_block(func_def.body)

        if not self._current_block_terminated():
            self._emit_return(None)

        return self._finalize_cfg()

    def build_module_init_cfg(self, tree: MypyFile) -> pir_pb2.PIRCFGProto:
        self._reset()
        self.current_label = 0
        self.current_instructions = []

        for defn in tree.defs:
            if isinstance(defn, (ExpressionStmt, AssignmentStmt)):
                self._visit_stmt(defn)
            # Skip FuncDef, ClassDef — they're extracted separately

        if not self._current_block_terminated():
            self._emit_return(None)

        return self._finalize_cfg()

    def _finalize_cfg(self) -> pir_pb2.PIRCFGProto:
        self._finalize_current_block()

        exit_labels = []
        for block in self.blocks:
            if block.instructions:
                last = block.instructions[-1]
                if (
                    last.HasField("return_inst")
                    or last.HasField("raise_inst")
                    or last.HasField("unreachable")
                ):
                    exit_labels.append(block.label)

        return pir_pb2.PIRCFGProto(
            blocks=self.blocks,
            entry_block=0,
            exit_blocks=exit_labels,
        )

    # ─── Statement dispatch ───────────────────────────────

    def _visit_block(self, block: Block):
        for stmt in block.body:
            self._visit_stmt(stmt)

    def _visit_stmt(self, stmt):
        if isinstance(stmt, AssignmentStmt):
            self._visit_assignment(stmt)
        elif isinstance(stmt, ExpressionStmt):
            self._visit_expression_stmt(stmt)
        elif isinstance(stmt, ReturnStmt):
            self._visit_return(stmt)
        elif isinstance(stmt, IfStmt):
            self._visit_if(stmt)
        elif isinstance(stmt, WhileStmt):
            self._visit_while(stmt)
        elif isinstance(stmt, ForStmt):
            self._visit_for(stmt)
        elif isinstance(stmt, TryStmt):
            self._visit_try(stmt)
        elif isinstance(stmt, WithStmt):
            self._visit_with(stmt)
        elif isinstance(stmt, RaiseStmt):
            self._visit_raise(stmt)
        elif isinstance(stmt, BreakStmt):
            self._visit_break()
        elif isinstance(stmt, ContinueStmt):
            self._visit_continue()
        elif isinstance(stmt, DelStmt):
            self._visit_del(stmt)
        elif isinstance(stmt, AssertStmt):
            self._visit_assert(stmt)
        elif isinstance(stmt, OperatorAssignmentStmt):
            self._visit_operator_assignment(stmt)
        elif isinstance(stmt, (PassStmt, GlobalDecl)):
            pass  # No-ops in IR
        elif isinstance(stmt, Block):
            self._visit_block(stmt)
        # Skip FuncDef, ClassDef, Import, etc. — handled at module level

    # ─── Assignment ───────────────────────────────────────

    def _visit_assignment(self, stmt: AssignmentStmt):
        rhs = self._accept_expr(stmt.rvalue)
        for lvalue in stmt.lvalues:
            self._assign_to(lvalue, rhs, getattr(stmt, "line", -1))

    def _assign_to(self, lvalue: Expression, rhs: pir_pb2.PIRValueProto, line: int):
        if isinstance(lvalue, NameExpr):
            target_name = self.scope.resolve_local(lvalue.name)
            target = pir_pb2.PIRValueProto(
                local=pir_pb2.PIRLocalProto(name=target_name)
            )
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=line,
                    assign=pir_pb2.PIRAssignProto(target=target, source=rhs),
                )
            )
        elif isinstance(lvalue, MemberExpr):
            obj = self._accept_expr(lvalue.expr)
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=line,
                    store_attr=pir_pb2.PIRStoreAttrProto(
                        object=obj,
                        attribute=lvalue.name,
                        value=rhs,
                    ),
                )
            )
        elif isinstance(lvalue, IndexExpr):
            obj = self._accept_expr(lvalue.base)
            index = self._accept_expr(lvalue.index)
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=line,
                    store_subscript=pir_pb2.PIRStoreSubscriptProto(
                        object=obj,
                        index=index,
                        value=rhs,
                    ),
                )
            )
        elif isinstance(lvalue, TupleExpr):
            targets = []
            for item in lvalue.items:
                if isinstance(item, NameExpr):
                    t_name = self.scope.resolve_local(item.name)
                    targets.append(
                        pir_pb2.PIRValueProto(local=pir_pb2.PIRLocalProto(name=t_name))
                    )
                elif isinstance(item, StarExpr) and isinstance(item.expr, NameExpr):
                    t_name = self.scope.resolve_local(item.expr.name)
                    targets.append(
                        pir_pb2.PIRValueProto(local=pir_pb2.PIRLocalProto(name=t_name))
                    )
                else:
                    targets.append(self._new_temp_value())
            star_index = -1
            for i, item in enumerate(lvalue.items):
                if isinstance(item, StarExpr):
                    star_index = i
                    break
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=line,
                    unpack=pir_pb2.PIRUnpackProto(
                        targets=targets,
                        source=rhs,
                        star_index=star_index,
                    ),
                )
            )
        elif isinstance(lvalue, StarExpr):
            self._assign_to(lvalue.expr, rhs, line)

    def _visit_operator_assignment(self, stmt: OperatorAssignmentStmt):
        from pir_server.builder.expression_visitor import _BIN_OP_MAP

        lhs_val = self._accept_expr(stmt.lvalue)
        rhs_val = self._accept_expr(stmt.rvalue)
        target = self._new_temp_value()
        op = _BIN_OP_MAP.get(stmt.op, pir_pb2.ADD)
        self._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(stmt, "line", -1),
                bin_op=pir_pb2.PIRBinOpProto(
                    target=target,
                    left=lhs_val,
                    right=rhs_val,
                    op=op,
                ),
            )
        )
        self._assign_to(stmt.lvalue, target, getattr(stmt, "line", -1))

    # ─── Expression statement ─────────────────────────────

    def _visit_expression_stmt(self, stmt: ExpressionStmt):
        self._accept_expr(stmt.expr)

    # ─── Return ───────────────────────────────────────────

    def _visit_return(self, stmt: ReturnStmt):
        value = self._accept_expr(stmt.expr) if stmt.expr else None
        self._emit_return(value, getattr(stmt, "line", -1))

    # ─── If ───────────────────────────────────────────────

    def _visit_if(self, stmt: IfStmt):
        end_block = self._new_block()

        for i, (cond_expr, body) in enumerate(zip(stmt.expr, stmt.body)):
            cond_val = self._accept_expr(cond_expr)
            true_block = self._new_block()
            if i < len(stmt.expr) - 1 or stmt.else_body:
                false_block = self._new_block()
            else:
                false_block = end_block

            self._emit_branch(
                cond_val, true_block, false_block, getattr(stmt, "line", -1)
            )

            self._activate(true_block)
            self._visit_block(body)
            if not self._current_block_terminated():
                self._emit_goto(end_block)

            if false_block != end_block:
                self._activate(false_block)

        if stmt.else_body:
            self._visit_block(stmt.else_body)
            if not self._current_block_terminated():
                self._emit_goto(end_block)

        self._activate(end_block)

    # ─── While ────────────────────────────────────────────

    def _visit_while(self, stmt: WhileStmt):
        header_block = self._new_block()
        body_block = self._new_block()
        exit_block = self._new_block()

        self._emit_goto(header_block)
        self._activate(header_block)

        cond = self._accept_expr(stmt.expr)
        self._emit_branch(cond, body_block, exit_block, getattr(stmt, "line", -1))

        old_break = self.break_target
        old_continue = self.continue_target
        self.break_target = exit_block
        self.continue_target = header_block

        self._activate(body_block)
        self._visit_block(stmt.body)
        if not self._current_block_terminated():
            self._emit_goto(header_block)

        self.break_target = old_break
        self.continue_target = old_continue

        self._activate(exit_block)
        if stmt.else_body:
            self._visit_block(stmt.else_body)

    # ─── For ──────────────────────────────────────────────

    def _visit_for(self, stmt: ForStmt):
        iter_val = self._new_temp_value()
        iterable_val = self._accept_expr(stmt.expr)
        self._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(stmt, "line", -1),
                get_iter=pir_pb2.PIRGetIterProto(
                    target=iter_val, iterable=iterable_val
                ),
            )
        )

        header_block = self._new_block()
        body_block = self._new_block()
        exit_block = self._new_block()

        self._emit_goto(header_block)
        self._activate(header_block)

        target_val = self._lower_for_target(stmt.index)
        self._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(stmt, "line", -1),
                next_iter=pir_pb2.PIRNextIterProto(
                    target=target_val,
                    iterator=iter_val,
                    body_block=body_block,
                    exit_block=exit_block,
                ),
            )
        )

        old_break = self.break_target
        old_continue = self.continue_target
        self.break_target = exit_block
        self.continue_target = header_block

        self._activate(body_block)
        # Emit tuple unpack if target is a tuple expression
        if isinstance(stmt.index, TupleExpr):
            self._emit_for_target_unpack(
                stmt.index, target_val, getattr(stmt, "line", -1)
            )
        self._visit_block(stmt.body)
        if not self._current_block_terminated():
            self._emit_goto(header_block)

        self.break_target = old_break
        self.continue_target = old_continue

        self._activate(exit_block)
        if stmt.else_body:
            self._visit_block(stmt.else_body)

    def _lower_for_target(self, target: Expression) -> pir_pb2.PIRValueProto:
        if isinstance(target, NameExpr):
            name = self.scope.resolve_local(target.name)
            return pir_pb2.PIRValueProto(local=pir_pb2.PIRLocalProto(name=name))
        if isinstance(target, TupleExpr):
            # For tuple unpacking targets like `for a, b in items:`,
            # use a temp for the next_iter result, then emit an unpack.
            temp = self._new_temp_value()
            return temp
        return self._new_temp_value()

    def _emit_for_target_unpack(
        self, target: Expression, iter_val: pir_pb2.PIRValueProto, line: int
    ):
        """After next_iter, emit unpack instructions for tuple targets."""
        if isinstance(target, TupleExpr):
            self._assign_to(target, iter_val, line)

    # ─── Try/Except ───────────────────────────────────────

    def _visit_try(self, stmt: TryStmt):
        handler_blocks = []
        for i in range(len(stmt.handlers)):
            handler_blocks.append(self._new_block())

        finally_block = self._new_block() if stmt.finally_body else None
        else_block = self._new_block() if stmt.else_body else None
        end_block = self._new_block()

        old_handlers = self.current_exception_handlers
        self.current_exception_handlers = [hb for hb in handler_blocks]

        self._visit_block(stmt.body)

        self.current_exception_handlers = old_handlers

        if not self._current_block_terminated():
            if else_block is not None:
                self._emit_goto(else_block)
            elif finally_block is not None:
                self._emit_goto(finally_block)
            else:
                self._emit_goto(end_block)

        for i, handler_body in enumerate(stmt.handlers):
            self._activate(handler_blocks[i])
            exc_type_protos = []
            if stmt.types[i] is not None:
                exc_type_protos.extend(self._resolve_except_types(stmt.types[i]))
            exc_target = None
            if stmt.vars[i] is not None:
                var_name = self.scope.resolve_local(stmt.vars[i].name)
                exc_target = pir_pb2.PIRValueProto(
                    local=pir_pb2.PIRLocalProto(name=var_name)
                )

            eh = pir_pb2.PIRExceptHandlerProto(exception_types=exc_type_protos)
            if exc_target:
                eh.target.CopyFrom(exc_target)
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(stmt, "line", -1),
                    except_handler=eh,
                )
            )

            self._visit_block(handler_body)
            if not self._current_block_terminated():
                if finally_block is not None:
                    self._emit_goto(finally_block)
                else:
                    self._emit_goto(end_block)

        if else_block is not None:
            self._activate(else_block)
            self._visit_block(stmt.else_body)
            if not self._current_block_terminated():
                if finally_block is not None:
                    self._emit_goto(finally_block)
                else:
                    self._emit_goto(end_block)

        if finally_block is not None:
            self._activate(finally_block)
            self._visit_block(stmt.finally_body)
            if not self._current_block_terminated():
                self._emit_goto(end_block)

        self._activate(end_block)

    # ─── With ─────────────────────────────────────────────

    def _visit_with(self, stmt: WithStmt):
        ctx_vals = []
        for i, ctx_expr in enumerate(stmt.expr):
            ctx_val = self._accept_expr(ctx_expr)
            ctx_vals.append(ctx_val)
            enter_result = self._new_temp_value()
            # Emit __enter__ call as method on context manager
            enter_attr = self._new_temp_value()
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(stmt, "line", -1),
                    load_attr=pir_pb2.PIRLoadAttrProto(
                        target=enter_attr,
                        object=ctx_val,
                        attribute="__enter__",
                    ),
                )
            )
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(stmt, "line", -1),
                    call=pir_pb2.PIRCallProto(
                        target=enter_result,
                        callee=enter_attr,
                        args=[],
                    ),
                )
            )
            if stmt.target and stmt.target[i]:
                self._assign_to(stmt.target[i], enter_result, getattr(stmt, "line", -1))

        self._visit_block(stmt.body)

        # Emit __exit__ calls in reverse order
        for ctx_val in reversed(ctx_vals):
            exit_attr = self._new_temp_value()
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(stmt, "line", -1),
                    load_attr=pir_pb2.PIRLoadAttrProto(
                        target=exit_attr,
                        object=ctx_val,
                        attribute="__exit__",
                    ),
                )
            )
            exit_result = self._new_temp_value()
            none_val = pir_pb2.PIRValueProto(
                const_val=pir_pb2.PIRConstProto(none_value=True)
            )
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(stmt, "line", -1),
                    call=pir_pb2.PIRCallProto(
                        target=exit_result,
                        callee=exit_attr,
                        args=[
                            pir_pb2.PIRCallArgProto(
                                value=none_val, kind=pir_pb2.POSITIONAL
                            ),
                            pir_pb2.PIRCallArgProto(
                                value=none_val, kind=pir_pb2.POSITIONAL
                            ),
                            pir_pb2.PIRCallArgProto(
                                value=none_val, kind=pir_pb2.POSITIONAL
                            ),
                        ],
                    ),
                )
            )

    # ─── Raise ────────────────────────────────────────────

    def _visit_raise(self, stmt: RaiseStmt):
        exc = self._accept_expr(stmt.expr) if stmt.expr else None
        cause = self._accept_expr(stmt.from_expr) if stmt.from_expr else None
        raise_proto = pir_pb2.PIRRaiseProto()
        if exc:
            raise_proto.exception.CopyFrom(exc)
        if cause:
            raise_proto.cause.CopyFrom(cause)
        self._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(stmt, "line", -1),
                raise_inst=raise_proto,
            )
        )

    # ─── Break / Continue ────────────────────────────────

    def _visit_break(self):
        if self.break_target is not None:
            self._emit_goto(self.break_target)

    def _visit_continue(self):
        if self.continue_target is not None:
            self._emit_goto(self.continue_target)

    # ─── Del ──────────────────────────────────────────────

    def _visit_del(self, stmt: DelStmt):
        expr = stmt.expr
        if isinstance(expr, NameExpr):
            local = pir_pb2.PIRValueProto(
                local=pir_pb2.PIRLocalProto(name=self.scope.resolve_local(expr.name))
            )
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(stmt, "line", -1),
                    delete_local=pir_pb2.PIRDeleteLocalProto(local=local),
                )
            )
        elif isinstance(expr, MemberExpr):
            obj = self._accept_expr(expr.expr)
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(stmt, "line", -1),
                    delete_attr=pir_pb2.PIRDeleteAttrProto(
                        object=obj, attribute=expr.name
                    ),
                )
            )
        elif isinstance(expr, IndexExpr):
            obj = self._accept_expr(expr.base)
            index = self._accept_expr(expr.index)
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(stmt, "line", -1),
                    delete_subscript=pir_pb2.PIRDeleteSubscriptProto(
                        object=obj, index=index
                    ),
                )
            )
        elif isinstance(expr, TupleExpr):
            for item in expr.items:
                # Recursively handle each item
                self._visit_del(
                    type(
                        "FakeDel", (), {"expr": item, "line": getattr(stmt, "line", -1)}
                    )()
                )

    # ─── Assert ───────────────────────────────────────────

    def _visit_assert(self, stmt: AssertStmt):
        cond = self._accept_expr(stmt.expr)
        pass_block = self._new_block()
        fail_block = self._new_block()
        self._emit_branch(cond, pass_block, fail_block, getattr(stmt, "line", -1))

        self._activate(fail_block)
        exc = pir_pb2.PIRValueProto(
            global_ref=pir_pb2.PIRGlobalRefProto(
                name="AssertionError", module="builtins"
            )
        )
        # Emit assert message as argument if present
        if stmt.msg:
            msg_val = self._accept_expr(stmt.msg)
            call_target = self._new_temp_value()
            self._emit(
                pir_pb2.PIRInstructionProto(
                    line_number=getattr(stmt, "line", -1),
                    call=pir_pb2.PIRCallProto(
                        target=call_target,
                        callee=exc,
                        args=[
                            pir_pb2.PIRCallArgProto(
                                value=msg_val, kind=pir_pb2.POSITIONAL
                            )
                        ],
                    ),
                )
            )
            exc = call_target
        self._emit(
            pir_pb2.PIRInstructionProto(
                line_number=getattr(stmt, "line", -1),
                raise_inst=pir_pb2.PIRRaiseProto(exception=exc),
            )
        )

        self._activate(pass_block)

    # ─── Helpers ──────────────────────────────────────────

    def _resolve_except_types(
        self, type_expr: Expression
    ) -> list[pir_pb2.PIRTypeProto]:
        """Resolve the exception type expression in an except clause."""
        result = []
        if isinstance(type_expr, TupleExpr):
            # except (TypeError, ValueError): ...
            for item in type_expr.items:
                result.extend(self._resolve_except_types(item))
        elif isinstance(type_expr, NameExpr):
            fullname = ""
            if type_expr.node and hasattr(type_expr.node, "fullname"):
                fullname = type_expr.node.fullname or ""
            if not fullname:
                fullname = f"builtins.{type_expr.name}"
            result.append(
                pir_pb2.PIRTypeProto(
                    class_type=pir_pb2.PIRClassTypeProto(qualified_name=fullname)
                )
            )
        elif isinstance(type_expr, MemberExpr):
            # e.g., except os.error: ...
            fullname = ""
            if type_expr.node and hasattr(type_expr.node, "fullname"):
                fullname = type_expr.node.fullname or ""
            if not fullname:
                fullname = f"{type_expr.name}"
            result.append(
                pir_pb2.PIRTypeProto(
                    class_type=pir_pb2.PIRClassTypeProto(qualified_name=fullname)
                )
            )
        else:
            # Fallback to generic Exception
            result.append(
                pir_pb2.PIRTypeProto(
                    class_type=pir_pb2.PIRClassTypeProto(
                        qualified_name="builtins.Exception"
                    )
                )
            )
        return result
