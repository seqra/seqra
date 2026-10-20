import random
import flask


def sink(data: str):
    pass


class Request:
    def __init__(self):
        self.tainted_attr: str = ""


def get_request() -> Request:
    return Request()


# Taint originates from an ATTRIBUTE read (not a call), then flows through an
# assignment chain into the sink. Exercises the attribute-source inversion in
# PIRMethodSequentPrecondition (sourcesForAttribute on a PIRLoadAttr).
def attr_source_to_sink():
    r = Request()
    a = r.tainted_attr
    b = a
    sink(b)


def conditional_attr_source_to_sink():
    r = get_request()
    a = r.tainted_attr
    b = a
    sink(b)


def weakrand_unconditional_sink():
    return random.normalvariate()


def call_side_effect_on_receiver():
    req = flask.request
    param = req.cookies.get("x")
    items = []
    items.append(param)
    bar = items[0]
    exec(bar)
    return "ok"


def container_literal_control():
    req = flask.request
    param = req.cookies.get("x")
    items = [param]
    bar = items.pop()
    exec(bar)
    return "ok"
