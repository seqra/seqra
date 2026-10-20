def TaintRuleFalsePositive(reason):
    def _decorator(fn):
        return fn
    return _decorator


def sink(data):
    pass


def prefix_clean(data):
    pass


def Positive_simple():
    data = ""
    sink(data)


def Positive_clean_second():
    data = ""
    sink(data)
    prefix_clean(data)


def Positive_clean_on_other_data():
    data = ""
    data1 = "aaa"
    prefix_clean(data1)
    sink(data)


@TaintRuleFalsePositive("Cleaner captures data before sink")
def Negative_clean_first():
    data = ""
    prefix_clean(data)
    sink(data)
