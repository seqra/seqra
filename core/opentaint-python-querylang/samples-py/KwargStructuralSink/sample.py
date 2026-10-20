def source() -> str:
    return "tainted"


def sink(x, mode) -> None:
    pass


def Positive_structural_kwarg_match():
    a = source()
    sink(a, mode="constant")     # arg0 tainted AND kwarg mode == "constant" -> should fire


def Negative_structural_kwarg_mismatch():
    a = source()
    sink(a, mode="other")        # kwarg mode != "constant" -> should NOT fire
