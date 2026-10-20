class Inner:
    def __init__(self, obj):
        self.obj = obj

    def get_obj_good(self):
        return self.obj

    def get_obj_bad(self):
        return self.obj


def src():
    return Inner(object())


def sink(data):
    pass


def Positive_simple():
    data = src()
    s = data.get_obj_bad()
    s = data.get_obj_good()
    sink(s)


def Negative_bad():
    data = src()
    s = data.get_obj_good()
    s = data.get_obj_bad()
    sink(s)
