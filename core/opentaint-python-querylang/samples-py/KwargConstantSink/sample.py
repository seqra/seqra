def source() -> str:
    return "tainted"


def sink(x, mode) -> None:
    pass


def Positive_kwarg_constant_match():
    t = source()
    sink(t, mode="constant")     # tainted arg AND kwarg mode == "constant" -> should fire


def Negative_kwarg_constant_mismatch():
    t = source()
    sink(t, mode="other")        # kwarg mode != "constant" -> should NOT fire


def Negative_not_tainted():
    sink("x", mode="constant")   # arg not tainted -> should NOT fire
