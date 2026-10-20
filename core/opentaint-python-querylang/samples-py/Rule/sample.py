def src() -> str:
    return "tainted string"


def clean(data: str) -> None:
    pass


def sink(data: str) -> None:
    pass


class StringContainer:
    def __init__(self) -> None:
        self.value = ""

    def get_value(self) -> str:
        return self.value

    def set_value(self, value: str) -> None:
        self.value = value


def sink_wrapper(data: str) -> None:
    sink(data)


def clean_sink_wrapper(data: str) -> None:
    clean(data)
    sink(data)


def Positive_simple():
    data = src()
    sink(data)


def Positive_simple_with_container():
    data = src()
    container = StringContainer()
    container.set_value(data)
    sink(container.get_value())


def Positive_with_ellipsis():
    data = src()
    print(data)
    sink(data)


def Positive_iter_proc():
    data = src()
    sink_wrapper(data)


def Negative_simple():
    data = src()
    clean(data)
    sink(data)


def Negative_with_ellipsis():
    data = src()
    print(data)
    clean(data)
    sink(data)


def Negative_iter_proc():
    data = src()
    clean_sink_wrapper(data)
