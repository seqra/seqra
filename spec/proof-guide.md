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

## Semantic scope

Every new or revised spec must state the selected major semantic changes
and the behavior that must remain unchanged. For each major change, show
current versus new behavior and identify the affected invariants and proofs.
Check the spec and its formal model for hidden changes outside that scope.
In particular, a representation, condition rewrite or correctness helper
can change field transfer, premise matching, layers or source/sink behavior.
Do not treat such a change as an implementation detail.

The previous mark-group/static iteration introduced unselected grounding
support and field-sensitive writer validation. Use this case as a regression
check for spec creation. Proofs of a changed model do not authorize a new
semantic contract. If implementation requires an unselected major change,
highlight it and obtain the intended behavior before implementing it, or
redesign within the selected scope. Do not add it silently to close a gap.
