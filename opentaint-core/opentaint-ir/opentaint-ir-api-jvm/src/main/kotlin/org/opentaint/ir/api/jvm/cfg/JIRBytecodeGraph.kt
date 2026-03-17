package org.opentaint.ir.api.jvm.cfg

import org.opentaint.ir.api.common.cfg.BytecodeGraph

interface JIRBytecodeGraph<out Statement> : BytecodeGraph<Statement>
