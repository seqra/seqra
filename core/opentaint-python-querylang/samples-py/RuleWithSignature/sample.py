def sink1(data):
    pass


def sink2(data):
    pass


def method_with_specific_signature1(x, data):
    sink1(data)


def method_with_specific_signature2(data):
    sink2(data)


def Positive_simple1():
    data = "aaa"
    method_with_specific_signature1(1, data)


def Positive_simple2():
    data = "aaa"
    method_with_specific_signature2(data)


def Negative_no_source():
    data = "aaa"
    sink1(data)
