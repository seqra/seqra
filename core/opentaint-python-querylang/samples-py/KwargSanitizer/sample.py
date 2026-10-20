def source() -> str:
    return "tainted"


def escape(value) -> str:
    return "safe"


def sink(y) -> None:
    pass


def Positive_unsanitized():
    t = source()
    sink(t)                  # tainted, not escaped -> should fire


def Negative_sanitized_via_kwarg():
    t = source()
    s = escape(value=t)      # escape(value=...) sanitizes -> result clean
    sink(s)                  # -> should NOT fire
