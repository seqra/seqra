def new_object_mapper() -> str:
    return "mapper"


def enable_default_typing(om: str) -> str:
    return om


def enable(om: str) -> str:
    return om


def foo(om: str) -> None:
    pass


def Positive_no_enable():
    om = new_object_mapper()
    mapper = enable_default_typing(om)
    foo(mapper)


def Negative_enable_before_foo():
    om = new_object_mapper()
    mapper = enable_default_typing(om)
    mapper = enable(mapper)
    foo(mapper)
