import ApSpec.Run1AllOrigins
import ApSpec.Run1RoutingProjection

#eval (ApSpec.Run1AllOrigins.controls.length, ApSpec.Run1RoutingProjection.controls.length)

#print axioms ApSpec.Run1AllOrigins.filter_empty
#print axioms ApSpec.Run1AllOrigins.some_mem_split
#print axioms ApSpec.Run1AllOrigins.zero_mem_split
#print axioms ApSpec.Run1AllOrigins.zero_requires_complete_binding
#print axioms ApSpec.Run1AllOrigins.nonzero_has_complete_binding
#print axioms ApSpec.Run1AllOrigins.ready_iff
#print axioms ApSpec.Run1AllOrigins.zeroReady_iff
#print axioms ApSpec.Run1AllOrigins.tuple_member
#print axioms ApSpec.Run1AllOrigins.complete_with_member
#print axioms ApSpec.Run1AllOrigins.shared_leaf_exact
#print axioms ApSpec.Run1AllOrigins.factor_zero_exact
#print axioms ApSpec.Run1AllOrigins.factor_nonzero_exact
#print axioms ApSpec.Run1AllOrigins.factor_exact
#print axioms ApSpec.Run1AllOrigins.handoff_pair_exact
#print axioms ApSpec.Run1AllOrigins.fresh_exact
#print axioms ApSpec.Run1AllOrigins.no_fresh_for_duplicate
#print axioms ApSpec.Run1AllOrigins.controls_pass
#print axioms ApSpec.Run1RoutingProjection.exact_class_field_disjoint
#print axioms ApSpec.Run1RoutingProjection.field_exact_class_disjoint
#print axioms ApSpec.Run1RoutingProjection.exact_match_has_prefix
#print axioms ApSpec.Run1RoutingProjection.remove_empty_effects
#print axioms ApSpec.Run1RoutingProjection.leaf_projection_idempotent
#print axioms ApSpec.Run1RoutingProjection.tree_projection_idempotent
#print axioms ApSpec.Run1RoutingProjection.identity_reuse_exact
#print axioms ApSpec.Run1RoutingProjection.immediate_consumer_skip
#print axioms ApSpec.Run1RoutingProjection.controls_pass
