class Inner:
    def __init__(self, obj):
        self.obj = obj

    def get_obj_good(self):
        return self.obj


def src():
    return Inner(object())


def sink(data):
    pass


def Positive_one_call():
    data = src()
    str = data.get_obj_good().to_string()
    sink(str)


def Positive_zero_calls():
    data = src()
    str = data.to_string()
    sink(str)


def Negative_two_calls():
    data = src()
    str = data.get_obj_good().get_class().to_string()
    sink(str)
