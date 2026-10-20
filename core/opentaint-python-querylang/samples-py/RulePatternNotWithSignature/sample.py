def TaintRuleFalsePositive(reason):
    def _decorator(fn):
        return fn
    return _decorator


def f(data):
    pass


def clean(data):
    pass


def Positive_simple():
    data = ""
    f(data)


def Negative_no_f():
    print("Hello!")


@TaintRuleFalsePositive("Cleaner captures data before sink")
def Negative_clean_first():
    data = ""
    clean(data)
    f(data)


def Negative_clean_second():
    data = ""
    f(data)
    clean(data)
