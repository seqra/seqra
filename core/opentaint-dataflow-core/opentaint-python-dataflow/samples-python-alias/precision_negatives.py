from helpers import alias_sink, Box


def negative_fresh_list_not_arg(a):
    r = [a]
    # alias: !arg0
    alias_sink(r)


def negative_fresh_dict_not_arg(a):
    r = {"k": a}
    # alias: !arg0
    alias_sink(r)


def negative_binary_expr_fresh(a, b):
    r = a + b
    # alias: !arg0, !arg1
    alias_sink(r)


def negative_fstring_fresh(a):
    r = f"{a}"
    # alias: !arg0
    alias_sink(r)


def positive_param_field_weak_update(b, x, y):
    b.data = x
    b.data = y
    dst = b.data
    # alias: arg0.data, arg1, arg2
    alias_sink(dst)


def positive_call_result_weak_update(x, y):
    b = Box()
    b.data = x
    b.data = y
    dst = b.data
    # alias: arg0, arg1
    alias_sink(dst)


def negative_reassign_after_branch(a, b, c):
    if c:
        r = a
    else:
        r = a
    r = b
    # alias: arg1, !arg0
    alias_sink(r)


def negative_constant_not_arg(a):
    r = 1
    # alias: !arg0
    alias_sink(r)


def negative_field_of_other_box(a, b, src):
    a.data = src
    dst = b.data
    # alias: arg1.data, !arg2
    alias_sink(dst)
