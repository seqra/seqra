class Final:
    def call2(self):
        return "c2"

    def other2(self):
        return "o2"


class Mid:
    def __init__(self):
        self.attr2 = Final()


class First:
    def call1(self):
        return Mid()

    def other1(self):
        return Mid()


class Root:
    def __init__(self):
        self.attr = First()


def src():
    return Root()


def sink(o):
    pass


def Positive_chain():
    a = src()
    res = a.attr.call1().attr2.call2()
    sink(res)


def Negative_wrong_terminal_call():
    a = src()
    res = a.attr.call1().attr2.other2()
    sink(res)


def Negative_wrong_middle_call():
    a = src()
    res = a.attr.other1().attr2.call2()
    sink(res)
