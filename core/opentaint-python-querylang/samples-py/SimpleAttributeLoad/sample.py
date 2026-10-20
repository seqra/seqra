import flask

def src():
    return "tainted"


def sink(o):
    pass


def clean(o):
    pass


def Positive_simple():
    data = flask.request.form.get("const")
    sink(data)

def Positive_split():
    req = flask.request
    data = req.form.get("const")
    sink(data)


def Negative_const_sink():
    data = flask.request.form.get("const")
    sink("...")

def Negative_invalid_attribute():
    data = flask.req.form.get("const")
    sink("...")

def Negative_small_chain():
    data = flask.request.form
    sink(data)

def Negative_invalid_last_attribute():
    data = flask.request.form.getValue("const")
    sink(data)
