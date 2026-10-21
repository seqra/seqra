# depth: 1
from helpers import alias_sink, pick, set_data, Box


def positive_kwarg_binds_by_name(a, b):
    r = pick(a, b=b)
    # alias: arg1, !arg0
    alias_sink(r)


def positive_kwargs_reordered(a, b):
    r = pick(b=a, a=b)
    # alias: arg0, !arg1
    alias_sink(r)


def positive_kwarg_setter(bx, src):
    set_data(v=src, b=bx)
    r = bx.data
    # alias: arg0.data, arg1
    alias_sink(r)


def positive_kwarg_ctor(src):
    b = Box(data=src)
    r = b.data
    # alias: arg0
    alias_sink(r)


def negative_kwarg_default_unbound(a):
    r = pick(a)
    # alias: !arg0
    alias_sink(r)
