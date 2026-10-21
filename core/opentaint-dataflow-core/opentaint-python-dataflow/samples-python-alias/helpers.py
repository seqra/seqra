from missing_lib import external


def alias_sink(x):
    pass


class Box:
    def __init__(self, data=None):
        self.data = data

    def get(self):
        return self.data

    def set(self, v):
        self.data = v

    def get_via(self):
        return self.get()


def identity(x):
    return x


def get_data(b):
    return b.data


def set_data(b, v):
    b.data = v


def wrap_once(x):
    return identity(x)


def wrap_twice(x):
    return wrap_once(x)


def get_data_via(b):
    return get_data(b)


def first(s):
    return s[0]


def make_returner(s):
    def inner():
        return s
    return inner


def pick(a, b=None):
    return b
