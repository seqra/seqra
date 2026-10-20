def source() -> str:
    return "tainted"


def sink(x, mode) -> None:
    pass


def Positive_constant_match():
    t = source()
    sink(t, "constant")     # tainted arg0 AND arg1 == "constant" -> should fire


def Negative_constant_mismatch():
    t = source()
    sink(t, "other")        # arg1 != "constant" -> should NOT fire


def Negative_not_tainted():
    sink("x", "constant")   # arg0 not tainted -> should NOT fire
