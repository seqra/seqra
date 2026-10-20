def src():
    return "tainted"


def clean(data):
    return data


def Positive_simple():
    sink = src()
    return sink


def Negative_cleaned():
    name = src()
    sink = clean(name)
    return sink
