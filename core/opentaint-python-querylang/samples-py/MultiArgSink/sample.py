def source() -> str:
    return "tainted"


def run(cmd, arg) -> None:
    pass


def Positive_arg_tainted():
    t = source()
    run("ls", t)        # taint in arg index 1 ($ARG) -> should fire


def Negative_cmd_tainted():
    t = source()
    run(t, "safe")      # taint in arg index 0 (the free temp $CMD) -> should NOT fire
