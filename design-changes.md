# Design Changes Log

This document tracks deviations from the original design and bugs discovered during quality testing.

## Changes

### DC-1: Exception Type Resolution in try/except
**Original design**: Hardcoded `builtins.Exception` for all except clause types.
**Problem**: All exception handlers appeared to catch the generic `Exception`, making exception-type-specific taint tracking impossible.
**Fix**: Added `_resolve_except_types()` method to `statement_visitor.py` that resolves `NameExpr` and `MemberExpr` exception types to their fully qualified names using mypy's symbol resolution. Handles tuple `except (A, B)` by recursing into each element.
**File**: `pir_server/builder/statement_visitor.py`

### DC-10: Mid-Block Terminators (Dead Code After return/raise)
**Original design**: `_visit_block()` visited all statements sequentially without checking if the current block was already terminated.
**Problem**: When a block contained a `return` or `raise` followed by more statements (e.g., `raise ValueError(...); x = 1` inside a `try` body, or `return` inside a `with` body followed by `__exit__` calls), the dead code was emitted into the same basic block after the terminator. This violates the basic block invariant that a terminator must be the last instruction.
**Impact**: Downstream analyses could misinterpret instructions after a terminator as reachable, producing incorrect data flow.
**Fix**: Added `_current_block_terminated()` check in `_visit_block()` loop — stops emitting statements after a terminator. Also added early return in `_visit_with()` when the body is terminated, preventing dead `__exit__` calls after a return.
**File**: `pir_server/builder/statement_visitor.py`

### DC-11: for/else and while/else Break Target
**Original design**: In `_visit_for()` and `_visit_while()`, the `break_target` was the same block as the `exit_block` where the `else` body was visited.
**Problem**: When the `else` body contained a `return`, the exit block was terminated. Then code after the for/while statement (which is reachable via the `break` path) was skipped because `_visit_block` saw the block was terminated. This caused the after-loop code to be lost.
**Fix**: For `for/else` and `while/else`, separate the `break_block` (after the else, where post-loop code goes) from the `else_block` (where the else body is visited). Break goes to `break_block`, normal loop exit goes to `else_block`. After visiting the else body, if it didn't terminate, emit a goto to `break_block`.
**File**: `pir_server/builder/statement_visitor.py`

### DC-12: with-stmt `__exit__` Dead Code
**Original design**: `_visit_with()` always emitted `__exit__` calls after visiting the body, regardless of whether the body terminated.
**Problem**: When the `with` body contained a `return` or `raise`, the `__exit__` calls were emitted after the terminator in the same basic block, creating dead code and violating the basic block invariant.
**Fix**: After visiting the body, check `_current_block_terminated()`. If true, skip `__exit__` emission entirely (the calls would be unreachable anyway).
**File**: `pir_server/builder/statement_visitor.py`

### DC-3: For-Loop Tuple Unpacking
**Original design**: `_lower_for_target` only handled `NameExpr` targets. For `for a, b in items:`, the tuple elements `a` and `b` were never assigned.
**Problem**: For-loop iteration variables in tuple unpacking patterns were invisible to the IR.
**Fix**: Added `_emit_for_target_unpack()` method. When the for-loop target is a `TupleExpr`, a temp variable receives the `next_iter` result, then a `PIRUnpack` instruction unpacks it into the individual variables.
**File**: `pir_server/builder/statement_visitor.py`

### DC-4: Function Qualified Names for Methods
**Original design**: Method qualified names were `module.method_name`, same as top-level functions.
**Problem**: `MyClass.foo` and a module-level `foo` would have the same qualified name, causing lookup collisions.
**Fix**: Added `enclosing_class` parameter to `_build_function()`. When set, qualified names become `module.ClassName.method_name`.
**File**: `pir_server/builder/module_builder.py`

### DC-5: Recursive Type Mapping Overflow
**Original design**: `TypeMapper.map()` had no recursion guard.
**Problem**: Some Python types (e.g., `packaging.version._BaseVersion`) have recursive type parameters that cause infinite recursion in `type_mapper.py`, crashing the entire analysis with `RecursionError`.
**Fix**: Added depth counter with `MAX_DEPTH = 10`. When exceeded, returns `PIRAnyType` as a safe fallback. Split into `map()` (guard) + `_map_inner()` (actual logic).
**File**: `pir_server/builder/type_mapper.py`

### DC-6: Integer Overflow for Large Python Constants
**Original design**: Python `int` values were directly assigned to protobuf `int64` field.
**Problem**: Python supports arbitrary-precision integers (e.g., `2**64` in `more_itertools`), but protobuf `int64` maxes at `2^63 - 1`. Values exceeding this caused `ValueError: Value out of range`.
**Fix**: Added range check in `_const_int()`. Values within int64 range use `int_value`; larger values fall back to `string_value` representation.
**File**: `pir_server/builder/expression_visitor.py`

### DC-7: Silent Exception Swallowing in CFG Building
**Original design**: `_build_function()` and `_build_module_init()` caught all exceptions silently, replacing the CFG with a stub.
**Problem**: Bugs in the lowering code were completely hidden, making quality analysis impossible.
**Fix**: Added `sys.stderr` logging with the function name, exception type, and message. The stub CFG is still used as fallback, but the failure is now visible.
**File**: `pir_server/builder/module_builder.py`

### DC-8: Assert Message Support
**Original design**: `_visit_assert` ignored `stmt.msg`.
**Problem**: `assert condition, "message"` lost the message argument.
**Fix**: When `stmt.msg` is present, the message is lowered and emitted as a `PIRCall` to the `AssertionError` constructor with the message as a positional argument.
**File**: `pir_server/builder/statement_visitor.py`

### DC-9: Exception Handler Labels Not Set on Try-Body Blocks
**Original design**: `_visit_try` set `current_exception_handlers` before visiting the try body, then restored it after. The intent was that `_finalize_current_block()` would stamp the handler labels onto each block as it was created.
**Problem**: The try body's blocks were only finalized when the next `_activate()` call happened (for handler blocks). By that time, `current_exception_handlers` had already been restored to the outer scope's handlers. Result: all blocks inside `try` bodies had `exceptionHandlers = []`, making exception flow invisible to the IR.
**Impact**: For taint analysis, this meant that exception-handler-mediated data flow (e.g., `except ValueError as e:` capturing tainted data) was structurally disconnected from the try body that produced the exception.
**Fix**: Two changes to `_visit_try()`:
1. Before setting exception handlers, activate a new block for the try body (so pre-try instructions are finalized without handlers).
2. After visiting the try body (and emitting the goto-to-end terminator), explicitly call `_finalize_current_block()` before restoring old handlers. This ensures the try body's last block gets the correct handler labels stamped on it.
**File**: `pir_server/builder/statement_visitor.py`
