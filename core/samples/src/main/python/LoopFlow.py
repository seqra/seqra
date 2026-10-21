def source() -> str:
    pass

def sink(data: str):
    pass

# P1: taint used inside loop body
def loop_while_body():
    x = source()
    i = 0
    while i < 10:
        sink(x)
        i = i + 1

# P1: taint used inside for loop body
def loop_for_body():
    x = source()
    for i in range(10):
        sink(x)
