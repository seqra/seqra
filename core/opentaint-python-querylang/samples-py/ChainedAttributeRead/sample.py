class Level2:
    def call1(self):
        return "x"

    def other(self):
        return "y"


class Level1:
    def __init__(self):
        self.attr2 = Level2()

    def call1(self):
        return "z"


class Root:
    def __init__(self):
        self.attr1 = Level1()


def src():
    return Root()


def sink(o):
    pass


def Positive_chain():
    a = src()
    res = a.attr1.attr2.call1()
    sink(res)


def Negative_wrong_last_call():
    a = src()
    res = a.attr1.attr2.other()
    sink(res)


def Negative_short_chain():
    a = src()
    res = a.attr1.call1()
    sink(res)
