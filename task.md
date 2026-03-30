We have dataflow based SAST analyzer. 

We have multiple configuration points:
1. Patterns in `rules/ruleset`. Vulnerable patterns to search in analyzed code. We treat all such patterns as a patterns on a dataflow trace and match them similar to the taint rules.
2. Approximations in `core/opentaint-config/config`. Default dataflow propagators for common libraries.
3. Approximations in `core/opentaint-jvm-sast-dataflow/dataflow-approximations`. Complex code-based propagators for complex methods.
4. Framework support like in `core/src/main/kotlin/org/opentaint/jvm/sast/project/spring/SpringWebProject.kt`. Special handling of frameworks.

We are trying to make all our rules and approximations customizable (e.g. via llm agent). Here are our requirements:
1. Agent should be able to generate patterns (1). We need to specialize all requirement to the pattern language
2. Agent should be able to debug and fix patterns (1). For example, fix FP or FN.
3. To work with FN, engine will return a list of external methods, where dataflow fact was killed
4. Agent should be able to generate approximations (2) and (3), mainly to fix FN
5. Approximations (2) must be hierarchical. For example, if we have rule for method `get*` and a rule for `getEntry`, the rule for `getEntry` must override the rule for `get*`
6. Approximations (3) always override (2)
7. Agent must be able to override (2) and (3)
8. Agent must be able to generate required missed approximations based on the list of external methods, where dataflow fact was killed
9. Frameworks support (4) provided as-is and is not configurable by the agent

Analyze current analyzer impl and requirements and propose the following documents:
1. pattern-rules.md: design of all thing related to pattern rules
2. approximations-config.md: all things related to approximations
3. agent-pipeline.md: whole pipeline for the agent to work with rules, common scenarios
