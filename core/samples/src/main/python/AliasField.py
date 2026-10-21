def source() -> str:
    pass


def sink(data: str):
    pass


def noop(x):
    pass


class Container:
    def __init__(self):
        self.data: str = ""


class Inner:
    def __init__(self):
        self.f: str = ""


class Outer:
    def __init__(self):
        self.inner: Inner = Inner()


class Box:
    def __init__(self):
        self.data: str = ""


class Node:
    def __init__(self):
        self.ref: "Node" = None
        self.data: str = ""


def link(x: Node, y: Node):
    x.ref = y


class Linker:
    def __init__(self):
        self.ref: "Node" = None

    def link(self, y: Node):
        self.ref = y


def takes_kw(x, y=None):
    pass


# P1: simple alias — b aliases a; taint written into a.data must reach b.data.
# Requires alias analysis: the copy b = a happens BEFORE a.data is tainted, so
# plain copy-propagation cannot carry the field fact.
def alias_simple():
    a = Container()
    b = a
    a.data = source()
    sink(b.data)


# Negative: a and b are distinct objects; taint into a.data must NOT reach b.data.
def alias_none():
    a = Container()
    b = Container()
    a.data = source()
    sink(b.data)


# Chain re-read: taint into a.inner.f reaches b.inner.f via alias b ~ a.
def alias_chain():
    a = Outer()
    b = a
    a.inner.f = source()
    sink(b.inner.f)


# Deviation: an opaque call between establishing the alias and tainting through it
# must NOT drop the alias (we leave alias state unchanged across calls).
def alias_through_call():
    a = Container()
    b = a
    noop(b)
    a.data = source()
    sink(b.data)


# Phase 2 — callee-created alias: link makes a.ref alias b. Tainting a.ref.data
# then reaches b.data only if the alias created inside link is inlined.
def alias_interproc():
    a = Node()
    b = Node()
    link(a, b)
    a.ref.data = source()
    sink(b.data)


# Phase 2 — receiver binding (instance method, offset==1): a.link(b) inlines with
# self bound to receiver a, so self.ref = y creates a.ref ~ b. Tainting a.ref.data
# then reaches b.data — exercises the offset==1 self-substitution path.
def alias_interproc_receiver():
    a = Linker()
    b = Node()
    a.link(b)
    a.ref.data = source()
    sink(b.data)


# Phase 2 — keyword arg must not crash inlining (unbound param → aliases nothing).
def alias_kwargs():
    a = Box()
    b = a
    takes_kw(a, y=b)
    a.data = source()
    sink(b.data)
