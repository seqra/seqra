/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.config

import org.opentaint.dataflow.ifds.AccessPath
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.fmap
import org.opentaint.dataflow.ifds.map
import org.opentaint.dataflow.taint.Tainted
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark

class TaintActionEvaluator(
    private val positionResolver: PositionResolver<Maybe<AccessPath>>,
) {
    fun evaluate(action: CopyAllMarks, fact: Tainted): Maybe<Collection<Tainted>> =
        positionResolver.resolve(action.from).map { from ->
            if (from != fact.variable) return@map Maybe.none()
            positionResolver.resolve(action.to).fmap { to ->
                setOf(fact, fact.copy(variable = to))
            }
        }

    fun evaluate(action: CopyMark, fact: Tainted): Maybe<Collection<Tainted>> {
        if (fact.mark != action.mark) return Maybe.none()
        return positionResolver.resolve(action.from).map { from ->
            if (from != fact.variable) return@map Maybe.none()
            positionResolver.resolve(action.to).fmap { to ->
                setOf(fact, fact.copy(variable = to))
            }
        }
    }

    fun evaluate(action: AssignMark): Maybe<Collection<Tainted>> =
        positionResolver.resolve(action.position).fmap { variable ->
            setOf(Tainted(variable, action.mark))
        }

    fun evaluate(action: RemoveAllMarks, fact: Tainted): Maybe<Collection<Tainted>> =
        positionResolver.resolve(action.position).map { variable ->
            if (variable != fact.variable) return@map Maybe.none()
            Maybe.some(emptySet())
        }

    fun evaluate(action: RemoveMark, fact: Tainted): Maybe<Collection<Tainted>> {
        if (fact.mark != action.mark) return Maybe.none()
        return positionResolver.resolve(action.position).map { variable ->
            if (variable != fact.variable) return@map Maybe.none()
            Maybe.some(emptySet())
        }
    }
}
