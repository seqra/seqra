class CustomType1:
    def mk_type2(self):
        return CustomType2()


class CustomType2:
    def mk_type3(self):
        return CustomType3()


class CustomType3:
    def mk_type1(self):
        return CustomType1()


def positive_simple(src):
    v3 = src.mk_type2().mk_type3()
    sink = v3.mk_type1()
    return sink


def Positive():
    positive_simple(CustomType1())


def positive_one_line_simple(src):
    return src.mk_type2().mk_type3().mk_type1()


def Positive_one_line():
    positive_one_line_simple(CustomType1())


def negative_simple(src):
    return src.mk_type2().mk_type3()


def Negative():
    negative_simple(CustomType1())
