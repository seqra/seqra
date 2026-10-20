def f(x: str) -> None:
    pass


def g(x: str) -> None:
    pass


def h(x: str) -> None:
    pass


def clean(x: str) -> None:
    pass


def Positive_both_g_and_h():
    x = "data"
    g(x)
    h(x)
    f(x)


def Positive_reverse_inside():
    x = "data"
    h(x)
    g(x)
    f(x)


def Positive_clean_last():
    x = "data"
    g(x)
    h(x)
    f(x)
    clean(x)


def Negative_only_g():
    x = "data"
    g(x)
    f(x)


def Negative_no_g():
    x = "data"
    h(x)
    f(x)


def Negative_with_clean():
    x = "data"
    g(x)
    h(x)
    clean(x)
    f(x)
