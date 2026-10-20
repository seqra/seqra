def src():
    return "tainted data"


class RClass:
    def include(self, f, s):
        pass


class XClass:
    def get_request_dispatcher(self):
        return RClass()


def Positive_simple():
    data = src()
    x = XClass()
    r = x.get_request_dispatcher()
    r.include(data, "const")
