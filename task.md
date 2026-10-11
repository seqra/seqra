Follow a proof-first, CEGAR-style, test-driven development approach   
for all the work, using Lean as the formal model. First, model all the       
necessary parts in Lean with the invariants proved. Then design a formal model of    
the feature and prove its invariants. Then use TDD to develop the feature.   
Write only constructive formal models: no classical axioms, and every existence  
proof must yield an executable witness. Refine the abstraction if it is      
not complete, but the abstraction should be sound (at least within the         
restricted scope in effect at any given moment). Use the formal model to     
find bugs in the design. Use the formal model to distinguish the concept from the           
optimization, and prove that they are functionally equivalent but that the   
optimization has a different complexity, or a different complexity over some practical      
scope. The core idea is for the implementation to be sound and complete      
within some scope. The wider the scope the better, but stay practical: working in      
real-world scenarios is crucial and is the guideline-quality gate.

Use ASD-STE100 Simplified Technical English language    

We are working on [bidirectional-task.md](bidirectional-task.md)

Read project memory from the previous session: `~/.claude/projects/-drive-testcomp-opentaint-go-rules-opentaint-w3/`

Now we have a complete spec and implementation proposal for the phases 1-3.
Our current task: create a detailed implementation plan.
The plan should step-by-step follow the implementation proposal. 
We should interleave between implementation and validation rounds.
Write it to the spec/plan.md

Use subagents.