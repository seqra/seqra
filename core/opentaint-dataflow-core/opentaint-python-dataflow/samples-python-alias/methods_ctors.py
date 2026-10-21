# depth: 1
from helpers import alias_sink, Box


def positive_method_getter(b: Box):
    r = b.get()
    # alias: arg0.data
    alias_sink(r)


def positive_method_setter_then_read(b: Box, src):
    b.set(src)
    r = b.data
    # alias: arg0.data, arg1
    alias_sink(r)


def positive_method_setter_then_getter(b: Box, src):
    b.set(src)
    r = b.get()
    # alias: arg0.data, arg1
    alias_sink(r)


def positive_ctor_stores_arg(src):
    b = Box(src)
    r = b.data
    # alias: arg0
    alias_sink(r)


def positive_ctor_then_getter(src):
    b = Box(src)
    r = b.get()
    # alias: arg0
    alias_sink(r)


def positive_local_box_field(src):
    b = Box()
    b.data = src
    r = b.data
    # alias: arg0
    alias_sink(r)


def negative_ctor_distinct_objects(a, c):
    b1 = Box(a)
    b2 = Box(c)
    r = b1.data
    # alias: arg0, !arg1
    alias_sink(r)


def negative_ctor_local_strong_update(x, y):
    b = Box()
    b.data = x
    b.data = y
    dst = b.data
    # alias: arg1, !arg0
    alias_sink(dst)


def positive_bound_method_sees_later_write(b: Box, src):
    t = b.get
    b.data = src
    r = t()
    # alias: arg0.data, arg1
    alias_sink(r)
