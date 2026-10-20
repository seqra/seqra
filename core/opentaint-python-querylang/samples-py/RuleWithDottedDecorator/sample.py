class App:
    def route(self, f):
        return f


app = App()
other_app = App()


# The rule names the decorator as written at the definition (`app.route`), while the IR
# qualifies it with the module (`sample.app.route`).
@app.route
def routed_method(o):
    return o


def undecorated_method(o):
    return o


# A different receiver: `sample.other_app.route` must not satisfy `@app.route`.
@other_app.route
def other_routed_method(o):
    return o


def Positive_routed():
    routed_method("data")


def Negative_undecorated():
    undecorated_method("data")


def Negative_other_receiver():
    other_routed_method("data")
