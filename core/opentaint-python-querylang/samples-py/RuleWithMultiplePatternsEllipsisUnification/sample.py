class CustomType1:
    def mk_type2(self):
        return CustomType2()

    @staticmethod
    def mk_type1_from_type3(t3):
        return CustomType1()


class CustomType2:
    def mk_type3(self):
        return CustomType3()


class CustomType3:
    def mk_type1(self):
        return CustomType1()


def Positive_simple():
    return simple(CustomType1())


def simple(src):
    v2 = src.mk_type2()
    v3 = v2.mk_type3()
    sink = CustomType1.mk_type1_from_type3(v3)
    return sink
