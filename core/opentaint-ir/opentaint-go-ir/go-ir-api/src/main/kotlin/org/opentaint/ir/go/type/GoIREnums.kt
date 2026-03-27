package org.opentaint.ir.go.type

enum class GoIRBinaryOp {
    ADD, SUB, MUL, DIV, REM,
    AND, OR, XOR, SHL, SHR, AND_NOT,
    EQ, NEQ, LT, LEQ, GT, GEQ;
}

enum class GoIRUnaryOp {
    NOT,    // logical negation
    NEG,    // arithmetic negation
    XOR,    // bitwise complement
    DEREF,  // pointer dereference (load)
    ARROW,  // channel receive
}

enum class GoIRCallMode { DIRECT, DYNAMIC, INVOKE }

enum class GoIRChanDirection { SEND_RECV, SEND_ONLY, RECV_ONLY }

enum class GoIRNamedTypeKind { STRUCT, INTERFACE, ALIAS, OTHER }
