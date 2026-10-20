def sink(data):
    pass


def suffix_clean(data):
    pass


def Positive_simple():
    data = ""
    sink(data)


def Positive_clean_first():
    data = ""
    suffix_clean(data)
    sink(data)


def Positive_clean_on_other_data():
    data = ""
    data1 = ""
    sink(data)
    suffix_clean(data1)


def Negative_clean_second():
    data = ""
    sink(data)
    suffix_clean(data)
