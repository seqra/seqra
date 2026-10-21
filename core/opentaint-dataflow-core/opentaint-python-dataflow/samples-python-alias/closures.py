# depth: 2
from helpers import alias_sink, make_returner


def positive_closure_captures_arg(src):
    def f():
        return src
    r = f()
    # alias: arg0
    alias_sink(r)


def positive_closure_captures_field(b):
    def f():
        return b.data
    r = f()
    # alias: arg0.data
    alias_sink(r)


def positive_closure_param(src):
    def f(x):
        return x
    r = f(src)
    # alias: arg0
    alias_sink(r)


def positive_lambda_identity(src):
    f = lambda x: x
    r = f(src)
    # alias: arg0
    alias_sink(r)


def negative_closure_ignores_capture(a, b):
    def f():
        return b
    r = f()
    # alias: arg1, !arg0
    alias_sink(r)


def positive_make_returner(src):
    f = make_returner(src)
    r = f()
    # alias: ?arg0
    alias_sink(r)
