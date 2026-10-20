def new_object_mapper() -> str:
    return "mapper"


def enable_default_typing(om: str) -> str:
    return om


def enable(om: str) -> str:
    return om


def foo(om: str) -> None:
    pass


def Positive_full_chain():
    om = new_object_mapper()
    mapper = enable_default_typing(om)
    foo(mapper)


def Negative_full_chain_with_enable():
    om = new_object_mapper()
    enable(om)
    mapper = enable_default_typing(om)
    foo(mapper)
