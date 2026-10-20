def entry_point(f):
    return f


def clean(o):
    return object()


@entry_point
def positive_method(o):
    return o


def Positive_entrypoint():
    positive_method("data")


@entry_point
def negative_method(o):
    return clean(o)


def Negative_entrypoint():
    negative_method("data")
