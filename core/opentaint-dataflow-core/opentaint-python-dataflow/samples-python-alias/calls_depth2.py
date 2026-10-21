# depth: 2
from helpers import alias_sink, identity, wrap_once, wrap_twice, get_data_via, Box


def positive_wrap_once_flows(src):
    r = wrap_once(src)
    # alias: arg0
    alias_sink(r)


def positive_get_data_via(b):
    r = get_data_via(b)
    # alias: arg0.data
    alias_sink(r)


def positive_identity_of_wrap(src):
    r = identity(wrap_once(src))
    # alias: arg0
    alias_sink(r)


def positive_method_get_via(b: Box):
    r = b.get_via()
    # alias: arg0.data
    alias_sink(r)


def negative_wrap_twice_needs_depth3(src):
    r = wrap_twice(src)
    # alias: !arg0
    alias_sink(r)
