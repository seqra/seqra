def source() -> str:
    return ""


def sink(data: str):
    pass


# Test: str.upper() preserves taint
def builtin_str_upper():
    data = source()
    upper = data.upper()
    sink(upper)


# Test: str.lower() preserves taint
def builtin_str_lower():
    data = source()
    lower = data.lower()
    sink(lower)


# Test: str.strip() preserves taint
def builtin_str_strip():
    data = source()
    stripped = data.strip()
    sink(stripped)


# Test: str.replace() preserves taint
def builtin_str_replace():
    data = source()
    replaced = data.replace("a", "b")
    sink(replaced)


# Test: str() constructor preserves taint
def builtin_str_constructor():
    data = source()
    s = str(data)
    sink(s)


# Test: str.encode() preserves taint (result is bytes, but still tainted)
def builtin_str_encode():
    data = source()
    encoded = data.encode("utf-8")
    sink(encoded)


# Test: str.format() preserves taint
def builtin_str_format():
    data = source()
    formatted = "prefix {}".format(data)
    sink(formatted)


# Test: f-string (desugared to format-like)
def builtin_fstring():
    data = source()
    result = f"prefix {data}"
    sink(result)


# Test: string concatenation preserves taint
def builtin_str_concat():
    data = source()
    result = "prefix" + data
    sink(result)


# Test: string concatenation tainted + safe
def builtin_str_concat_reverse():
    data = source()
    result = data + "suffix"
    sink(result)
