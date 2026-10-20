def f(x: str) -> None:
    pass


def g(x: str) -> None:
    pass


def h(x: str) -> None:
    pass


def clean(x: str) -> None:
    pass


def Positive_simple():
    x = "data"
    f(x)
    g(x)
    h(x)


def Positive_reverse_inside():
    x = "data"
    f(x)
    h(x)
    g(x)


def Positive_clean_first():
    x = "data"
    clean(x)
    f(x)
    g(x)
    h(x)


def Negative_with_clean():
    x = "data"
    f(x)
    clean(x)
    g(x)
    h(x)


def Negative_no_g():
    x = "data"
    f(x)
    h(x)


def Negative_no_h():
    x = "data"
    f(x)
    g(x)
