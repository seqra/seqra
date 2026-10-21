from helpers import alias_sink, identity, get_data, set_data, wrap_once, external, make_returner


def negative_identity_no_flow(src):
    r = identity(src)
    # alias: !arg0
    alias_sink(r)


def negative_getter_no_flow(b):
    r = get_data(b)
    # alias: !arg0.data
    alias_sink(r)


def negative_wrap_once_no_flow(src):
    r = wrap_once(src)
    # alias: !arg0
    alias_sink(r)


def negative_external_no_flow(src):
    r = external(src)
    # alias: !arg0
    alias_sink(r)


def negative_setter_no_getter_flow(b, src):
    set_data(b, src)
    r = get_data(b)
    # alias: !arg1
    alias_sink(r)


def negative_identity_of_field(b):
    r = identity(b.data)
    # alias: !arg0.data
    alias_sink(r)


def negative_make_returner_no_flow(src):
    f = make_returner(src)
    r = f()
    # alias: !arg0
    alias_sink(r)


def negative_method_getter_no_flow(b):
    r = b.get()
    # alias: !arg0.data
    alias_sink(r)


def positive_field_read_survives_call(b, src):
    b.data = src
    identity(b)
    dst = b.data
    # alias: arg0.data, arg1
    alias_sink(dst)
