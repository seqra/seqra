/-
  Selected grouped-F1 descriptor grammar. Nonzero concrete IDs below denote
  GROUP IDs, not full marks; group 0 remains distinct from control ZERO by
  the place constructor. Ordinary base IDs encode as b+2 (neither ZERO nor S).
  Cleaner temporaries are explicit rejected raw inputs. Static descriptors
  are point/read descriptors, not a permission to seed a method at entry.
  Current clarification (20 October 2026): a class point is an exact global
  mark carrier. The historical classChain family is rejected. There are
  eight current families. No class-wide mark or class abstraction exists.
  This local grammar/layer model does not certify frontend or scheduler routes.
-/
import ApSpec.RequestGatedStatics

namespace ApSpec.GroupedRun1Initials
open ApSpec Run1Requests
set_option autoImplicit false

abbrev GroupId := Nat
inductive Shape where
  | zero
  | abstractBase (base : Nat)
  | abstractField (cls field : Nat)
  | baseExact (base mark : Nat)
  | baseChain (base mark : Nat)
  | fieldExact (cls field mark : Nat)
  | fieldChain (cls field mark : Nat)
  | classExact (cls mark : Nat)
  deriving DecidableEq, Repr

abbrev RawInitial := Run1InitialShapes.RawInitial

def toBaseline : Shape → RequestGatedStatics.InitialGrammar.Shape
  | .zero => .zero
  | .abstractBase b => .abstractBase b
  | .abstractField c f => .abstractField c f
  | .baseExact b g => .baseExact b g
  | .baseChain b g => .baseChain b g
  | .fieldExact c f g => .fieldExact c f g
  | .fieldChain c f g => .fieldChain c f g
  | .classExact c g => .classExact c g

def baselineEncode (shape : Shape) : RawInitial :=
  RequestGatedStatics.InitialGrammar.encode (toBaseline shape)

/-- The only historical family removed by the exact-class clarification. -/
def fromBaseline : (old : RequestGatedStatics.InitialGrammar.Shape) →
    Option {shape : Shape // toBaseline shape = old}
  | .zero => some ⟨.zero, rfl⟩
  | .abstractBase b => some ⟨.abstractBase b, rfl⟩
  | .abstractField c f => some ⟨.abstractField c f, rfl⟩
  | .baseExact b g => some ⟨.baseExact b g, rfl⟩
  | .baseChain b g => some ⟨.baseChain b g, rfl⟩
  | .fieldExact c f g => some ⟨.fieldExact c f g, rfl⟩
  | .fieldChain c f g => some ⟨.fieldChain c f g, rfl⟩
  | .classExact c g => some ⟨.classExact c g, rfl⟩
  | .classChain _ _ => none

inductive RawInput where
  | ap (raw : RawInitial)
  | cleanerTemporary (id : Nat)
  deriving DecidableEq, Repr

def descriptor (shape : Shape) : RawInput := .ap (baselineEncode shape)
def Legal (input : RawInput) : Prop := ∃ shape : Shape, descriptor shape = input

/-- Successful parsing returns the constructor and a proof of exact encoding. -/
def parse : (input : RawInput) → Option {shape : Shape // descriptor shape = input}
  | .ap raw => match RequestGatedStatics.InitialGrammar.classify raw with
    | none => none
    | some old => match fromBaseline old.val with
      | none => none
      | some shape => some ⟨shape.val, by
          unfold descriptor baselineEncode
          rw [shape.property]
          exact congrArg RawInput.ap old.property⟩
  | .cleanerTemporary _ => none

theorem parse_descriptor (shape : Shape) : (parse (descriptor shape)).isSome = true := by
  cases shape <;> rfl

theorem classification (input : RawInput) : (parse input).isSome = true ↔ Legal input := by
  constructor
  · intro accepted
    cases found : parse input with
    | none => simp [found] at accepted
    | some shape => exact ⟨shape.val, shape.property⟩
  · rintro ⟨shape, rfl⟩
    exact parse_descriptor shape

/-- Current descriptors are a strict refinement of the historical grammar.
    A historical classChain witness is not a current legal descriptor. -/
theorem baseline_compatible (raw : RawInitial) (legal : Legal (.ap raw)) :
    RequestGatedStatics.InitialGrammar.Legal raw := by
  obtain ⟨shape, same⟩ := legal
  exact ⟨toBaseline shape, RawInput.ap.inj same⟩

theorem selected_subset (raw : RawInitial) (legal : Legal (.ap raw)) :
    RequestGatedStatics.InitialGrammar.Legal raw := baseline_compatible raw legal

theorem historical_subset (raw : RawInitial) (legal : Legal (.ap raw)) :
    Run1InitialShapes.Legal raw := by
  obtain ⟨shape, same⟩ := selected_subset raw legal
  exact ⟨RequestGatedStatics.InitialGrammar.oldShape shape, same⟩

theorem empty_E (raw : RawInitial) (legal : Legal (.ap raw)) : raw.exclusion = .empty :=
  Run1InitialShapes.legal_empty_E raw (historical_subset raw legal)
theorem no_ANY (raw : RawInitial) (legal : Legal (.ap raw)) : raw.tail ≠ .any :=
  Run1InitialShapes.legal_no_any raw (historical_subset raw legal)
theorem no_MUST (raw : RawInitial) (legal : Legal (.ap raw)) : raw.tail ≠ .must :=
  Run1InitialShapes.legal_no_must raw (historical_subset raw legal)
theorem empty_mx (raw : RawInitial) (legal : Legal (.ap raw)) (mx : List Mark) :
    raw.mark ≠ .starEx mx :=
  Run1InitialShapes.legal_no_excluded_initial_mark raw (historical_subset raw legal) mx

theorem no_root (tail : Run1InitialShapes.InitialTail) (mark : MarkA) (e : Excl) :
    ¬ Legal (.ap ⟨.at .root, tail, mark, e⟩) := by
  intro legal
  obtain ⟨shape, same⟩ := legal
  cases shape <;> simp [descriptor, baselineEncode, toBaseline,
    RequestGatedStatics.InitialGrammar.encode,
    RequestGatedStatics.InitialGrammar.oldShape, Run1InitialShapes.encode] at same

theorem no_nonstatic_field (b next : Nat) (rest : List Acc)
    (tail : Run1InitialShapes.InitialTail) (mark : MarkA) (e : Excl) :
    ¬ Legal (.ap ⟨.at (.local b (next :: rest)), tail, mark, e⟩) :=
  fun legal => Run1InitialShapes.no_nonstatic_field_initial b next rest tail mark e
    (historical_subset _ legal)
theorem no_static_deep (c f next : Nat) (rest : List Acc)
    (tail : Run1InitialShapes.InitialTail) (mark : MarkA) (e : Excl) :
    ¬ Legal (.ap ⟨.staticDeep c f next rest, tail, mark, e⟩) :=
  fun legal => Run1InitialShapes.no_static_deep_initial c f next rest tail mark e
    (historical_subset _ legal)
theorem no_abstract_class (c : Nat) (tail : Run1InitialShapes.InitialTail) (e : Excl) :
    ¬ Legal (.ap ⟨.at (.cls c), tail, .star, e⟩) :=
  fun legal => Run1InitialShapes.no_abstract_class c tail e (historical_subset _ legal)
theorem no_class_chain (c g : Nat) (e : Excl) :
    ¬ Legal (.ap ⟨.at (.cls c), .star, .conc g, e⟩) := by
  rintro ⟨shape, same⟩
  cases shape <;> simp [descriptor, baselineEncode, toBaseline,
    RequestGatedStatics.InitialGrammar.encode,
    RequestGatedStatics.InitialGrammar.oldShape, Run1InitialShapes.encode] at same

theorem class_initial_is_exact (raw : RawInitial) (c : Nat)
    (legal : Legal (.ap raw)) (atClass : raw.place = .at (.cls c)) : raw.tail = .exact := by
  obtain ⟨shape, same⟩ := legal
  have encoded : baselineEncode shape = raw := RawInput.ap.inj same
  subst raw
  cases shape <;> simp_all [baselineEncode, toBaseline,
    RequestGatedStatics.InitialGrammar.encode,
    RequestGatedStatics.InitialGrammar.oldShape, Run1InitialShapes.encode]

theorem baseline_class_chain_strictly_rejected (c g : Nat) :
    RequestGatedStatics.InitialGrammar.Legal ⟨.at (.cls c), .star, .conc g, .empty⟩ ∧
      ¬ Legal (.ap ⟨.at (.cls c), .star, .conc g, .empty⟩) :=
  ⟨⟨.classChain c g, rfl⟩, no_class_chain c g .empty⟩
theorem no_cleaner_temporary (id : Nat) : ¬ Legal (.cleanerTemporary id) := by
  rintro ⟨shape, same⟩
  cases same

def isGroup : Shape → Bool
  | .zero | .abstractBase _ | .abstractField _ _ => false
  | _ => true
def entryDemand : Shape → Bool
  | .zero | .abstractBase _ => false
  | _ => true
def entry (shape : Shape) : AFact :=
  { startFact (RequestGatedStatics.InitialGrammar.toAP (toBaseline shape)) with demand := entryDemand shape }

theorem group_initial_DEMAND (shape : Shape) (group : isGroup shape = true) :
    (entry shape).demand = true := by
  cases shape <;> simp_all [isGroup, entry, entryDemand]
theorem entry_payload_is_baseline (shape : Shape) :
    (entry shape).fact = (startFact (RequestGatedStatics.InitialGrammar.toAP (toBaseline shape))).fact := rfl
theorem exact_group_changes_old_NORMAL (b : Nat) (g : GroupId) :
    (startFact (RequestGatedStatics.InitialGrammar.toAP (.baseExact b g))).demand = false ∧
      (entry (.baseExact b g)).demand = true := ⟨rfl, rfl⟩

/-- A point reply relabels an already surviving current relation. It does not
    invoke startFact. Admission, original/support replacement and K6 are adapters. -/
def pointRefine (current : PFact) (group : GroupId) : AFact :=
  ⟨{ current with mark := .conc group }, true⟩
theorem point_refinement_payload (current : PFact) (group : GroupId) :
    (pointRefine current group).fact = { current with mark := .conc group } ∧
      (pointRefine current group).demand = true := ⟨rfl, rfl⟩
theorem point_refinement_keeps_tail (current : PFact) (group : GroupId) :
    (pointRefine current group).fact.kind = current.kind := rfl

def samples : List Shape := [
  .zero, .abstractBase 0, .abstractField 0 1, .baseExact 0 0,
  .baseChain 0 0, .fieldExact 0 1 0, .fieldChain 0 1 0, .classExact 0 0]
theorem eight_families : samples.length = 8 := rfl
theorem all_eight_parse : samples.all (fun shape => (parse (descriptor shape)).isSome) = true := by decide
def rejected : List RawInput := [
  .ap ⟨.at .root, .star, .star, .empty⟩, .ap ⟨.at .root, .exact, .conc 0, .empty⟩,
  .ap ⟨.at (.local 0 [1]), .star, .star, .empty⟩,
  .ap ⟨.staticDeep 0 1 2 [], .star, .conc 0, .empty⟩,
  .ap ⟨.at (.cls 0), .star, .star, .empty⟩,
  .ap ⟨.at (.cls 0), .star, .conc 0, .empty⟩,
  .ap ⟨.at (.field 0 1), .any, .conc 0, .empty⟩,
  .ap ⟨.at (.field 0 1), .must, .conc 0, .empty⟩,
  .ap ⟨.at (.field 0 1), .star, .star, .set [2]⟩,
  .ap ⟨.at (.field 0 1), .star, .starEx [0], .empty⟩, .cleanerTemporary 0]
theorem eleven_rejections : rejected.length = 11 ∧ rejected.all (fun raw => (parse raw).isNone) = true := by decide
theorem entry_and_point_controls :
    (entry (.baseExact 0 0)).demand = true ∧
    (entry (.fieldExact 0 1 0)).demand = true ∧
    (entry (.classExact 0 0)).demand = true ∧
    (entry (.baseChain 0 0)).fact.kind = .any ∧
    (entry (.abstractField 0 1)).demand = true ∧
    (entry (.abstractBase 0)).demand = false ∧
    (pointRefine (RequestGatedStatics.InitialGrammar.toAP (.abstractField 0 1)) 0).fact.kind =
      .star .empty := by decide

namespace Controls
def controls : List (String × Bool) :=
  samples.mapIdx (fun index shape => (s!"family-{index}", (parse (descriptor shape)).isSome)) ++
  rejected.mapIdx (fun index raw => (s!"reject-{index}", (parse raw).isNone)) ++
  [("base-exact-group-D", (entry (.baseExact 0 0)).demand),
   ("field-exact-group-D", (entry (.fieldExact 0 1 0)).demand),
   ("class-exact-group-D", (entry (.classExact 0 0)).demand),
   ("entry-chain-current-ANY", decide ((entry (.baseChain 0 0)).fact.kind = .any)),
   ("static-FLOW-D", (entry (.abstractField 0 1)).demand),
   ("independent-base-FLOW-N", !(entry (.abstractBase 0)).demand),
   ("point-keeps-STAR", decide ((pointRefine
      (RequestGatedStatics.InitialGrammar.toAP (.abstractField 0 1)) 0).fact.kind = .star .empty))]
end Controls
theorem controls_count : Controls.controls.length = 26 := rfl
theorem controls_pass : Controls.controls.all Prod.snd = true := by decide

#print axioms classification
#print axioms baseline_compatible
#print axioms no_root
#print axioms group_initial_DEMAND
#print axioms point_refinement_payload

end ApSpec.GroupedRun1Initials
