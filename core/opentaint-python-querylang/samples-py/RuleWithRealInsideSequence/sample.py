class ObjectMapper:
    def enable_default_typing(self):
        pass

    def read_value(self, json):
        return ""


def Positive_simple():
    om = ObjectMapper()
    om.enable_default_typing()
    om.read_value("")


def Negative_simple():
    om = ObjectMapper()
    om.read_value("{}")
    om.enable_default_typing()
