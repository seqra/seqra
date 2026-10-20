class CustomType1:
    def mk_type2(self):
        return CustomType2()


class CustomType2:
    pass


def positive_src_else_derived_simple(src):
    if src is not None:
        ret = src
    else:
        ret = CustomType1()
    return ret.mk_type2()


def Positive_src_else_derived():
    positive_src_else_derived_simple(CustomType1())


def negative_always_safe_simple(src):
    safe = CustomType1()
    if True:
        safe = CustomType1()
    return safe.mk_type2()


def Negative_always_safe():
    negative_always_safe_simple(CustomType1())
