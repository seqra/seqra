def source() -> str:
    pass

def sink(data: str):
    pass

# Helper functions for chained calls

def chain2_g(b: str) -> str:
    return b

def chain2_f(a: str) -> str:
    return chain2_g(a)

def chain3_h(c: str) -> str:
    return c

def chain3_g(b: str) -> str:
    return chain3_h(b)

def chain3_f(a: str) -> str:
    return chain3_g(a)

# P1: two-level call chain
def call_chain_2():
    sink(chain2_f(source()))

# P1: three-level call chain
def call_chain_3():
    sink(chain3_f(source()))
