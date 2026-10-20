def source() -> str:
    return "tainted"


def clean(x: str) -> str:
    return x


def Positive_no_clean():
    x = source()
    return x


def Negative_cleaned():
    x = source()
    clean(x)
    return x
