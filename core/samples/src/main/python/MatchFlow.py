def source() -> str:
    pass

def sink(data: str):
    pass

# P1: capture binds the subject; tainted -> reachable
def match_capture():
    x = source()
    match x:
        case y:
            sink(y)

# P1: value-pattern case then wildcard; both bodies reachable (overapprox)
def match_value_then_wildcard():
    x = source()
    match x:
        case "quit":
            sink(x)
        case _:
            sink(x)

# P1: `as` binds the subject; tainted -> reachable
def match_as():
    x = source()
    match x:
        case "hello" as y:
            sink(y)

# P1: guarded case; taint reaches the body (overapprox -> reachable)
def match_guard():
    x = source()
    match x:
        case y if len(y) > 2:
            sink(y)

# N: subject is clean, capture binds clean data -> not reachable
def match_capture_clean():
    x = "safe"
    match x:
        case y:
            sink(y)
