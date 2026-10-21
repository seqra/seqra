# depth: 1
from helpers import alias_sink, identity, get_data, set_data, wrap_once, first, pick


def positive_identity_flows(src):
    r = identity(src)
    # alias: arg0
    alias_sink(r)


def positive_getter_field(b):
    r = get_data(b)
    # alias: arg0.data
    alias_sink(r)


def positive_setter_then_getter(b, src):
    set_data(b, src)
    r = get_data(b)
    # alias: arg0.data, arg1
    alias_sink(r)


def positive_identity_chain(src):
    a = identity(src)
    r = identity(a)
    # alias: arg0
    alias_sink(r)


def positive_identity_then_field(b):
    v = get_data(b)
    r = identity(v)
    # alias: arg0.data
    alias_sink(r)


def positive_setter_then_direct_read(b, src):
    set_data(b, src)
    r = b.data
    # alias: arg0.data, arg1
    alias_sink(r)


def positive_identity_of_field(b):
    r = identity(b.data)
    # alias: arg0.data
    alias_sink(r)


def positive_getter_after_direct_write(b, src):
    b.data = src
    r = get_data(b)
    # alias: arg0.data, arg1
    alias_sink(r)


def positive_identity_list_elem(s):
    r = identity(s[0])
    # alias: arg0[]
    alias_sink(r)


def positive_first_elem(s):
    r = first(s)
    # alias: arg0[]
    alias_sink(r)


def positive_cond_identity(a, b, c):
    if c:
        r = identity(a)
    else:
        r = identity(b)
    # alias: arg0, arg1
    alias_sink(r)


def negative_wrap_once_needs_depth2(src):
    r = wrap_once(src)
    # alias: !arg0
    alias_sink(r)


def negative_pick_returns_second(a, b):
    r = pick(a, b)
    # alias: arg1, !arg0
    alias_sink(r)
