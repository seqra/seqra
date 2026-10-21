import base64
import html
import shlex


def source() -> str:
    return ""


def sink(data):
    pass


def drop_arg(data: str) -> str:
    return "safe"


def mixed_callee_unknown_pass_rule(cond: bool):
    data = source()
    fn = drop_arg if cond else base64.b64decode
    result = fn(data)
    sink(result)


def mixed_callee_real_only(cond: bool):
    data = source()
    fn = drop_arg if cond else drop_arg
    result = fn(data)
    sink(result)


def unknown_callees_one_cleaner(cond: bool):
    data = source()
    fn = shlex.quote if cond else html.escape
    fn(data)
    sink(data)


def unknown_callee_cleaner():
    data = source()
    shlex.quote(data)
    sink(data)


def tainted_callee_value_survives_call():
    fn = source()
    fn()
    sink(fn)
