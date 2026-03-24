def source() -> str:
    pass


def sink(data: str):
    pass

def sample():
    data = source()
    other = data
    sink(other)

def sample_non_reachable():
    data = source()
    other = "safe"
    sink(other)

