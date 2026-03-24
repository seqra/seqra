def source() -> str:
    pass


def sink(data: str):
    pass


def sample():
    data = source()
    other = data
    sink(other)
