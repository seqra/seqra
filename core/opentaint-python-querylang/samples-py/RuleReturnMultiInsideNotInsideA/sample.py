class CustomType1:
    def mk_type2(self):
        return CustomType2()


class CustomType2:
    def mk_type3(self):
        return CustomType3()


class CustomType3:
    def mk_type1(self):
        return CustomType1()


def Positive_entrypoint():
    simple_positive(CustomType1())


def simple_positive(src):
    v2 = src.mk_type2()
    sink = v2.mk_type3()
    return sink


def Negative_entrypoint():
    simple_negative(CustomType1())


def simple_negative(src):
    v2 = src.mk_type2()
    sink = v2.mk_type3()
    sanitize_a(sink)
    return sink


def sanitize_a(t):
    pass
