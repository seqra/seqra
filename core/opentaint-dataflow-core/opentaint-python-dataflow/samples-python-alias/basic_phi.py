from helpers import alias_sink


def positive_direct_copy(src):
    x = src
    # alias: arg0
    alias_sink(x)


def positive_copy_chain(src):
    x = src
    y = x
    z = y
    # alias: arg0
    alias_sink(z)


def positive_if_else(a, b, c):
    if c:
        r = a
    else:
        r = b
    # alias: arg0, arg1, !arg2
    alias_sink(r)


def positive_if_no_else(a, b, c):
    r = a
    if c:
        r = b
    # alias: arg0, arg1
    alias_sink(r)


def positive_loop_swap(a, b, n):
    x = a
    y = b
    for _ in range(n):
        t = x
        x = y
        y = t
    # alias: arg0, arg1
    alias_sink(x)


def positive_while_reassign(a, b, c):
    r = a
    while c:
        r = b
    # alias: arg0, arg1
    alias_sink(r)


def negative_overwrite_kills(a, b):
    r = a
    r = b
    # alias: arg1, !arg0
    alias_sink(r)


def negative_unrelated_param(a, b):
    r = a
    # alias: arg0, !arg1
    alias_sink(r)
