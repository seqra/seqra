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


def Positive_():
    data = src()
    str = data.get_obj_good().to_string()
    sink(str)


def Negative_():
    data = src()
    str = data.get_obj_bad().to_string()
    sink(str)
