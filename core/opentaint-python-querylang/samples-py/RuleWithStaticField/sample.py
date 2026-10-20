class StaticConstantStorage:
    FIRST = "FIRST"
    SECOND = "SECOND"


def sink(condition):
    pass


def Positive_simple():
    sink(StaticConstantStorage.FIRST)


def Negative_simple():
    sink(StaticConstantStorage.SECOND)
