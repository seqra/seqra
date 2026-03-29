We build dataflow analysis engine for the Go language.
Currently, we are working on basic analysis prototype

We have Go IR (GoIR) implemented in core/opentaint-ir/go
We have analysis stub in core/opentaint-dataflow-core/opentaint-go-dataflow
And we have test setup in core/src/test/kotlin/org/opentaint/go/sast/dataflow with test samples in core/samples/src/main/go
You can run tests using gradle `:test --tests "org.opentaint.go.sast.dataflow.SampleTest"`

Also, we have similar feature full dataflow engine for the JVM: core/opentaint-dataflow-core/opentaint-jvm-dataflow

Your task is:
1. Analyze, how we can implement required features for minimal dataflow prototype
   - Call resolver
   - Method analysis context
   - Flow function Sequential/Call
2. Design flow functions and how to apply Go taint rules
3. Design other required entities

**General info about core framework**
Read /home/sobol/IdeaProjects/opentaint/edges-knowledge.md

Write all your findings into go-dataflow-design.md

Now let's design test system for our analysis engine.
Currently we have 2 simple test cases, but we need much more.

My suggestion is:
1. Adapt test cases from `~/data/ant-application-security-testing-benchmark/sast-go`
   Copy test cases into our samples dir, performing required rewritings
   Consider this as a list of test cases for various language features

2. Generate test cases for various data flow features:
   - Interprocedural flow, interfaces
   - Field sensitivity, arrays
   - Collections
   - etc.

Write all your findings into go-dataflow-test-system.md

Now let's define implementation details.

1. Divide logic by modules, describe changes into every module
2. Align initially proposed dataflow design with planned test system requirements
3. Plan required pass rules for std library

Write all your findings into go-dataflow-implementation-details.md. 

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
