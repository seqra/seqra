def entry_point(f):
    return f


def other_decorator(f):
    return f


@entry_point
def decorated_method(o):
    return o


# Same shape as decorated_method, but undecorated: the rule requires @entry_point,
# so this must not be treated as an entry point.
def undecorated_method(o):
    return o


# Decorated, but with a different decorator: the gate must discriminate on the name,
# not merely on a decorator being present.
@other_decorator
def other_decorated_method(o):
    return o


def Positive_entrypoint():
    decorated_method("data")


def Negative_undecorated():
    undecorated_method("data")


def Negative_other_decorator():
    other_decorated_method("data")
