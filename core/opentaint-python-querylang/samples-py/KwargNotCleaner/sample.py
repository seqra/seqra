def source() -> str:
    return "tainted"


def transform(x, mode, label="") -> None:
    pass


def sink(a) -> None:
    pass


def Positive_unsafe_mode():
    a = source()
    transform(a, mode="unsafe")
    sink(a)                       # mode != "safe" -> not cleaned -> should fire


def Positive_other_kwarg_is_safe():
    a = source()
    transform(a, mode="unsafe", label="safe")
    sink(a)                       # only the label is "safe"; kwarg(mode) guard must not clean -> should fire


def Negative_safe_mode():
    a = source()
    transform(a, mode="safe")
    sink(a)                       # mode == "safe" -> cleaned -> should NOT fire
