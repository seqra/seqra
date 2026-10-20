def TaintRuleFalsePositive(reason):
    def _decorator(fn):
        return fn
    return _decorator


def foo(data):
    return None


def positive_method(arg):
    x = foo(None)
    return x


def Positive_entrypoint():
    positive_method("data")


def negative_method(arg):
    x = foo(arg)
    return x


@TaintRuleFalsePositive("Cleaner captures data before sink")
def Negative_entrypoint():
    negative_method("data")
