We build dataflow analysis engine for the Python language.
Currently, we are working on basic analysis prototype

We have Python IR (PIR) implemented in core/opentaint-ir/python
We have analysis stub in core/opentaint-dataflow-core/opentaint-python-dataflow
And we have test setup in core/src/test/kotlin/org/opentaint/python/sast/dataflow with test samples in core/samples/src/main/python
You can run tests using gradle `:test --tests "org.opentaint.python.sast.dataflow.PythonDataflowTest"`

Also, we have similar feature full dataflow engine for the JVM: core/opentaint-dataflow-core/opentaint-jvm-dataflow

Your task is:
1. Analyze, how we can implement required features for minimal dataflow prototype
   - Call resolver
   - Method analysis context (e.g. in python variable names are strings, but the dataflow core requires int indices for local vars)
   - Flow function Sequential/Call
2. Design flow functions and how to apply taint Python taint rules
3. Design other required entities

**General info about core framework**
We have 3 type of edges:
Zero2Zero -> simple reachability edge, mainly used to trigger taint sources
Zero2Fact -> i.e. Source edge. Means that we have source in this function on in some deeper called function. Has no abstractions (Universal exclusion used)
Fact2Fact -> i.e. Pass edge. Means that for example if arg(0) is tainted (initial fact, fact at method entry) then local(7) is tainted (fact at statement inside method). Use abstractions.

Abstractions.
F2F edges maybe abstracted. For example, if we have method:
```
def foo (arg):
   return arg
```
and we call foo with fact arg(0).f.g.x.tainted we  don't need to propagate exat fact. Instead, we try analyze foo with an abstract edge arg(0).* -> ret.*
`*` is an abstraction point. It means that we can concatenate same suffix to the initial fact of F2F edge and to the final, and the resul edge will be valid.
However, sometimes we need to refine abstraction. For example:
```
def getX (arg):
   return arg.x
```
Here we do the following:
1. Start analysis with arg(0).*
2. at arg.x we use exclusion set {x} to identify that we have edge with all suffixes not starting with x arg(0).* / {x}
3. Framework automatically tries refine fact and restart analysis with arg(0).x.* if such a fact exists

Note that we must do the same thing for taint marks, since they are also abstracted.

For example, see FinalFactReader


Write all your findings into python-dataflow-design.md

Now let's design test system for our analysis engine.
Currently we have 2 simple test cases, but we need much more.

My suggestion is:
1. Adapt test cases from `~/data/ant-application-security-testing-benchmark/sast-python2` and `~/data/ant-application-security-testing-benchmark/sast-python3`
2. Generate test cases for various data flow features:
   - Interprocedural flow
   - Field sensitivity
   - etc.

Write all your findings into python-dataflow-test-system.md

Now let's define implemetation details. 

1. Divide logic by modules, describe changes into every module
2. Allign initially proposed dataflow design with planned test system requirements

Write all your findings into python-dataflow-implementation-details.md. 

OK, WE ARE HERE. We have designed all we need and cant start implementing things.

Implement designed modules/changes.

Write plan.md with tasks. Include implementation and test steps. 
Track progress in plan.md file.

If you see a new task (e.g. you need to fix compilation failure) add it to the plan and then start working on it.
Always read plan.md before going to the next task
Use git to fix progress in the VCS

The result success criteria:
1. We have a dataflow analyzer implementation
2. We have full test system
3. All tests passed


