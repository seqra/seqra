"""Maps mypy Type objects to PIRTypeProto messages."""

from __future__ import annotations
from mypy.types import (
    Type,
    Instance,
    CallableType,
    UnionType,
    TupleType,
    NoneType,
    AnyType,
    UninhabitedType,
    TypeVarType,
    LiteralType,
    get_proper_type,
)
from pir_server.proto import pir_pb2


class TypeMapper:
    def map(self, typ: Type | None) -> pir_pb2.PIRTypeProto:
        if typ is None:
            return pir_pb2.PIRTypeProto(any_type=pir_pb2.PIRAnyTypeProto())

        typ = get_proper_type(typ)
        proto = pir_pb2.PIRTypeProto()

        if isinstance(typ, Instance):
            ct = pir_pb2.PIRClassTypeProto(
                qualified_name=typ.type.fullname,
            )
            for arg in typ.args:
                ct.type_args.append(self.map(arg))
            proto.class_type.CopyFrom(ct)

        elif isinstance(typ, CallableType):
            ft = pir_pb2.PIRFunctionTypeProto(
                return_type=self.map(typ.ret_type),
            )
            for arg_type in typ.arg_types:
                ft.param_types.append(self.map(arg_type))
            proto.function_type.CopyFrom(ft)

        elif isinstance(typ, UnionType):
            non_none = [
                t for t in typ.items if not isinstance(get_proper_type(t), NoneType)
            ]
            has_none = len(non_none) < len(typ.items)
            if len(non_none) == 1 and has_none:
                inner = self.map(non_none[0])
                if inner.HasField("class_type"):
                    inner.class_type.is_optional = True
                    return inner
            ut = pir_pb2.PIRUnionTypeProto()
            for item in typ.items:
                ut.members.append(self.map(item))
            proto.union_type.CopyFrom(ut)

        elif isinstance(typ, TupleType):
            tt = pir_pb2.PIRTupleTypeProto()
            for item in typ.items:
                tt.element_types.append(self.map(item))
            proto.tuple_type.CopyFrom(tt)

        elif isinstance(typ, NoneType):
            proto.none_type.CopyFrom(pir_pb2.PIRNoneTypeProto())

        elif isinstance(typ, AnyType):
            proto.any_type.CopyFrom(pir_pb2.PIRAnyTypeProto())

        elif isinstance(typ, UninhabitedType):
            proto.never_type.CopyFrom(pir_pb2.PIRNeverTypeProto())

        elif isinstance(typ, TypeVarType):
            tv = pir_pb2.PIRTypeVarTypeProto(name=typ.name)
            if typ.upper_bound:
                tv.bounds.append(self.map(typ.upper_bound))
            proto.type_var_type.CopyFrom(tv)

        elif isinstance(typ, LiteralType):
            lt = pir_pb2.PIRLiteralTypeProto(
                value=str(typ.value),
                base_type=self.map(typ.fallback),
            )
            proto.literal_type.CopyFrom(lt)

        else:
            proto.any_type.CopyFrom(pir_pb2.PIRAnyTypeProto())

        return proto
