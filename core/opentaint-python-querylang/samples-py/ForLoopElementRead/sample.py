def source() -> list:
    return ["tainted"]


def sink(x) -> None:
    pass


def Positive_for_loop_element():
    # Iterating a tainted collection should taint the loop variable, but PIRNextIter /
    # PIRIterExpr are unhandled in the sequent flow function (fall through to `unchanged`),
    # so `item` never receives element taint and the sink is (wrongly) not reported.
    for item in source():
        sink(item)
