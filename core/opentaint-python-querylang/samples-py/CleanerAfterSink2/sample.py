def TaintRuleFalsePositive(reason):
    def _decorator(fn):
        return fn
    return _decorator


def src():
    return object()


def copy(o):
    return object()


def sink(a, b):
    pass


def clean(a, b):
    pass


def Positive_simple():
    a = src()
    b = copy(a)
    sink(a, b)


@TaintRuleFalsePositive("Cleaner requires 2 facts")
def Negative_simple():
    a = src()
    b = copy(a)
    sink(a, b)
    clean(a, b)


def _multiple_functions_nested_src():
    return src()


def _multiple_functions_nested_sink(a, b):
    sink(a, b)


def Positive_multiple_functions():
    a = _multiple_functions_nested_src()
    b = copy(a)
    _multiple_functions_nested_sink(a, b)


def _negative_multiple_functions_nested_src():
    return src()


def _negative_multiple_functions_nested_sink(a, b):
    sink(a, b)


def _negative_multiple_functions_nested_clean(a, b):
    clean(a, b)


@TaintRuleFalsePositive("Cleaner requires 2 facts")
def Negative_multiple_functions():
    a = _negative_multiple_functions_nested_src()
    b = copy(a)
    _negative_multiple_functions_nested_sink(a, b)
    _negative_multiple_functions_nested_clean(a, b)


apply_clean = False


def _positive_branch_nested_src():
    return src()


def _positive_branch_nested_sink(a, b):
    sink(a, b)


def _positive_branch_nested_clean(a, b):
    if apply_clean:
        clean(a, b)


def Positive_branch():
    a = _positive_branch_nested_src()
    b = copy(a)
    _positive_branch_nested_sink(a, b)
    _positive_branch_nested_clean(a, b)


def _negative_branch_nested_src():
    return src()


def _negative_branch_nested_sink(a, b):
    sink(a, b)


def _negative_branch_other_clean(a, b):
    clean(a, b)


def _negative_branch_nested_clean(a, b):
    if apply_clean:
        clean(a, b)
    else:
        _negative_branch_other_clean(a, b)


@TaintRuleFalsePositive("Cleaner requires 2 facts")
def Negative_branch():
    a = _negative_branch_nested_src()
    b = copy(a)
    _negative_branch_nested_sink(a, b)
    _negative_branch_nested_clean(a, b)
