import io
import os.path
import threading

import requests

import callres_ns.pkg.mod


def source() -> str:
    return ""


def sink(data):
    pass


class FallbackTarget:
    def fallback_target(self, data):
        sink(data)


def fallback_unknown_receiver(u):
    r = requests.get(u)
    r.fallback_target(source())


class SinkingBase:
    def run_inherited(self, data):
        sink(data)


class SafeBase:
    def run_inherited(self, data):
        pass


class SinkingSvc(SinkingBase, SafeBase):
    pass


class SafeSvc(SafeBase, SinkingBase):
    pass


class UsesSinkingSvc:
    def __init__(self) -> None:
        self.svc: SinkingSvc = SinkingSvc()

    def go(self):
        self.svc.run_inherited(source())


class UsesSafeSvc:
    def __init__(self) -> None:
        self.svc: SafeSvc = SafeSvc()

    def go(self):
        self.svc.run_inherited(source())


def inherited_method_sinking():
    UsesSinkingSvc().go()


def inherited_method_safe():
    UsesSafeSvc().go()


class SinkingPropTarget:
    def prop_target(self, data):
        sink(data)


class SafePropTarget:
    def prop_target(self, data):
        pass


class HasSinkingProp:
    @property
    def p(self) -> SinkingPropTarget:
        return SinkingPropTarget()

    def go(self):
        self.p.prop_target(source())


class HasSafeProp:
    @property
    def p(self) -> SafePropTarget:
        return SafePropTarget()

    def go(self):
        self.p.prop_target(source())


def property_sinking():
    HasSinkingProp().go()


def property_safe():
    HasSafeProp().go()


class ThreadHelper:
    def thread_helper_run(self, data):
        sink(data)


class ThreadUser(threading.Thread):
    def __init__(self) -> None:
        self.helper = ThreadHelper()

    def go(self):
        self.helper.thread_helper_run(source())


def instance_attribute_with_external_base():
    ThreadUser().go()


class NamedThread(threading.Thread):
    def go(self):
        sink(self.name)


def external_base_attribute():
    NamedThread().go()


class Cfg:
    secret: str = ""


class SubCfg(Cfg):
    pass


def read_own_attribute(c: Cfg):
    sink(c.secret)


def read_inherited_attribute(c: SubCfg):
    sink(c.secret)


class Handler:
    def __call__(self, data):
        sink(data)


def instance_call():
    h = Handler()
    h(source())


class BaseInit:
    def __init__(self, data) -> None:
        sink(data)


class DerivedInit(BaseInit):
    pass


def inherited_init():
    DerivedInit(source())


class MyError(ValueError):
    pass


def external_constructor():
    MyError(source())


def namespace_package_call():
    callres_ns.pkg.mod.ns_target(source())


class DynTarget:
    def dyn_target(self, data):
        sink(data)


def real_module_miss():
    callres_ns.pkg.mod.dyn_target(source())


def resolved_callee_chain_name():
    os.path.join(source(), "x")


class UsesBuffer:
    def __init__(self) -> None:
        self.buf: io.StringIO = io.StringIO()

    def go(self):
        self.buf.write(source())


def external_annotated_field():
    UsesBuffer().go()


class ProjectBaseView:
    def mixin_helper(self, data):
        sink(data)


class MixinFirstView(threading.Thread, ProjectBaseView):
    def go(self):
        self.mixin_helper(source())


def external_mixin_before_project_base():
    MixinFirstView().go()


class ProjectWriter:
    def write(self, data):
        sink(data)
