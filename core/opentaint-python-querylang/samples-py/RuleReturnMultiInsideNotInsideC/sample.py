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
    t1 = src.mk_type2()
    sink = t1.mk_type3()
    return sink


def Negative_entrypoint():
    simple_negative(CustomType1())


def simple_negative(src):
    t1 = src.mk_type2()
    sanitize_c(t1)
    sink = t1.mk_type3()
    return sink


def Negative2_entrypoint():
    simple_negative2(CustomType1())


def simple_negative2(src):
    t1 = src.mk_type2()
    sink = t1.mk_type3()
    sanitize_c(sink)
    return sink


def Positive2_entrypoint():
    simple_positive2(CustomType1())


def simple_positive2(src):
    t1 = src.mk_type2()
    sink = t1.mk_type3()
    sanitize_c(src)
    return sink


def sanitize_c(o):
    pass
