def source() -> str:
    pass

def sink(data: str):
    pass

# P1: taint through if-true branch
def branch_if_true():
    x = source()
    if True:
        sink(x)

# P1: taint flows through both branches
def branch_if_else_both():
    if True:
        x = source()
    else:
        x = source()
    sink(x)

# P1: taint flows through one branch (overapprox -> reachable)
def branch_if_else_one():
    if True:
        x = source()
    else:
        x = "safe"
    sink(x)

# P1: overwrite in one branch, taint may survive (overapprox -> reachable)
def branch_overwrite_in_branch():
    x = source()
    if True:
        x = "safe"
    sink(x)
