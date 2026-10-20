class CustomType1:
    def mk_type2(self):
        return CustomType2()


class CustomType2:
    def mk_type3(self):
        return CustomType3()

    def clean(self):
        pass


class CustomType3:
    def mk_type1(self):
        return CustomType1()

    def sanitize(self):
        pass


def Positive_entrypoint():
    simple_positive(CustomType1())


def simple_positive(src):
    ret = src.mk_type2().mk_type3().mk_type1()
    return ret


def Negative_entrypoint():
    simple_negative(CustomType1())


def simple_negative(src):
    ret = src.mk_type2().mk_type3().mk_type1()
    sanitize(ret)
    return ret


def sanitize(t):
    pass
