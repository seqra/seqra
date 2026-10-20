def source() -> str:
    return "tainted"


def sink(data) -> None:
    pass


def sink_wrapper(data) -> None:
    sink(data)


def Positive_tainted():
    data = source()
    sink(data)              # tainted, not the allowed literal -> should fire


def Positive_with_ellipsis():
    data = source()
    print(data)             # intervening statement, taint still flows
    sink(data)


def Positive_iter_proc():
    data = source()
    sink_wrapper(data)      # interprocedural sink via wrapper


def Negative_allowed_constant():
    sink("ok")              # exactly the allowed literal "ok" -> excluded, should NOT fire


def Negative_other_constant():
    sink("nope")            # not tainted (a constant), also not "ok" -> should NOT fire
