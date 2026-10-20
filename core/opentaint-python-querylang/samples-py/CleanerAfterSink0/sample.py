def src():
    return object()


def sink(o):
    pass


def clean():
    pass


def Positive_simple():
    o = src()
    sink(o)


def Negative_simple():
    o = src()
    sink(o)
    clean()


def _multiple_functions_nested_src():
    return src()


def _multiple_functions_nested_sink(o):
    sink(o)


def Positive_multiple_functions():
    o = _multiple_functions_nested_src()
    _multiple_functions_nested_sink(o)


def _negative_multiple_functions_nested_src():
    return src()


def _negative_multiple_functions_nested_sink(o):
    sink(o)


def _negative_multiple_functions_nested_clean(o):
    clean()


def Negative_multiple_functions():
    o = _negative_multiple_functions_nested_src()
    _negative_multiple_functions_nested_sink(o)
    _negative_multiple_functions_nested_clean(o)


apply_clean = False


def _positive_branch_nested_src():
    return src()


def _positive_branch_nested_sink(o):
    sink(o)


def _positive_branch_nested_clean(o):
    if apply_clean:
        clean()


def Positive_branch():
    o = _positive_branch_nested_src()
    _positive_branch_nested_sink(o)
    _positive_branch_nested_clean(o)


def _negative_branch_nested_src():
    return src()


def _negative_branch_nested_sink(o):
    sink(o)


def _negative_branch_other_clean(o):
    clean()


def _negative_branch_nested_clean(o):
    if apply_clean:
        clean()
    else:
        _negative_branch_other_clean(o)


def Negative_branch():
    o = _negative_branch_nested_src()
    _negative_branch_nested_sink(o)
    _negative_branch_nested_clean(o)
