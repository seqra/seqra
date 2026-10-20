def mk_type2(x: str) -> str:
    return x


def mk_type3(x: str) -> str:
    return x


def mk_type1(x: str) -> str:
    return x


def Positive_full_chain():
    v2 = mk_type2("data")
    v3 = mk_type3(v2)
    sink = mk_type1(v3)
    return sink


def Positive_one_line():
    return mk_type1(mk_type3(mk_type2("data")))


def Negative_skip_step():
    v2 = mk_type2("data")
    sink = mk_type1(v2)
    return sink


def Negative_v1():
    v2 = mk_type2("data")
    v3 = mk_type3(v2)
    return v3
