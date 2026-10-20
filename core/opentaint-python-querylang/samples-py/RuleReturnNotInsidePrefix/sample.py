class CustomType1:
    def mk_type2(self):
        return CustomType2()


class CustomType2:
    def mk_type3(self):
        return CustomType3()

def clean(arg):
    return


class CustomType3:
    def mk_type1(self):
        return CustomType1()


def Positive_entrypoint():
    simple_positive(CustomType1())


def simple_positive(src):
    s = src.mk_type2()
    return s.mk_type3().mk_type1()


def Negative_entrypoint():
    simple_negative(CustomType1())


def simple_negative(src):
    s = src.mk_type2()
    clean(s)
    return s.mk_type3().mk_type1()
