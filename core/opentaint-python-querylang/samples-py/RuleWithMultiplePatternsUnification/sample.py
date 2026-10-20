class CustomType1:
    def mk_type2(self):
        return CustomType2()


class CustomType2:
    def mk_type3(self):
        return CustomType3()


class CustomType3:
    def __init__(self, t1=None):
        pass

    def mk_type1(self):
        return CustomType1()


def Negative_simple():
    return simple_negative(CustomType1())


def simple_negative(src):
    sink = CustomType3(src)
    return sink


def Positive_other():
    return simple_positive(CustomType1())


def simple_positive(src):
    unused = src.mk_type2()
    sink = CustomType3(src)
    return sink
