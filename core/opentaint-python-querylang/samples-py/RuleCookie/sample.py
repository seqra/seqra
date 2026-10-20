class Cookie:
    def __init__(self, name, value):
        self.name = name
        self.value = value
        self.secure = False

    def set_secure(self, flag):
        self.secure = flag


class HttpServletResponse:
    def add_cookie(self, cookie):
        pass


def Positive_simple():
    resp = HttpServletResponse()
    c = Cookie("sid", "abc")
    resp.add_cookie(c)


def Negative_simple():
    resp = HttpServletResponse()
    c = Cookie("sid", "abc")
    c.set_secure(True)
    resp.add_cookie(c)
