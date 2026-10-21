def source() -> str:
    pass

def sink(data: str):
    pass

class Residual:
    def __init__(self):
        self.a: str = ""
        self.b: str = ""

    # `self` arrives abstracted (self.*). Reading `self.a` excludes `a` from the
    # abstraction; the residual self.*\{a} must survive so the later read of a
    # different field `self.b` still sees taint and reaches the sink.
    def leak_after_read(self):
        seen = self.a
        sink(self.b)

# The only sink is on `self.b`, read AFTER `self.a` inside the callee. Reaching it
# depends entirely on the abstract receiver's residual surviving the first field
# read's accessor exclusion — the both-ends refinement in handleAttrRead.
def receiver_residual_field():
    obj = Residual()
    obj.a = source()
    obj.b = source()
    obj.leak_after_read()
