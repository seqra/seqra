class CustomType1:
    pass


def _positive_simple(src):
    ret = src
    return ret


def Positive_simple():
    _positive_simple(CustomType1())


def _negative_simple(src):
    safe = CustomType1()
    return safe


def Negative_simple():
    _negative_simple(CustomType1())
