import random


def src():
    return "tainted string " + generate_string(random.Random(), "abc", 3)


def sink(data):
    pass


def Positive_simple():
    data = src()
    sink(data)


def Positive_with_ellipsis():
    data = src()
    print(data)
    sink(data)


def Positive_iter_proc():
    data = src()
    sink_wrapper(data)


def sink_wrapper(data):
    sink(data)


def Negative_simple():
    sink("Constant")


def generate_string(rng, characters, length):
    text = []
    for i in range(length):
        text.append(characters[rng.randint(0, len(characters) - 1)])
    return "".join(text)
