/-
  Selected-mark cleaners at one unchanged AP point.
  The concrete reference removes exact full marks. The run-1 normal channel
  excludes whole groups and the requested demand channel keeps those groups.
  These are executable local operations, not assumptions about a JVM solver.
-/
import ApSpec.RuleGroupMarks
import ApSpec.RuleGroupSchedule

namespace ApSpec.RuleGroupRequestRepair
open ApSpec.RuleGroupMarks

def excludedGroups (cleaners : List OwnedMark) : List Nat :=
  cleaners.map OwnedMark.group

/-- Independent scalar request oracle: one offer per executed cleaner. -/
def oracleRequests (cleaners : List OwnedMark) : List Nat :=
  cleaners.map OwnedMark.group

/-- The storage merges equal group interests. -/
def mergedRequests (cleaners : List OwnedMark) : List Nat :=
  (oracleRequests cleaners).eraseDups

def exactTrace (cleaners supplied : List OwnedMark) : List OwnedMark :=
  supplied.filter (fun m => decide (m ∉ cleaners))

def normalRecords (cleaners supplied : List OwnedMark) : List OwnedMark :=
  supplied.filter (fun m => decide (m.group ∉ excludedGroups cleaners))

/-- Requests are not evidence. A repair requires the actual supplied member. -/
def demandRepairs (cleaners supplied : List OwnedMark) : List OwnedMark :=
  supplied.filter (fun m => decide (m.group ∈ mergedRequests cleaners))

theorem merged_requests_exact (cleaners : List OwnedMark) (g : Nat) :
    g ∈ mergedRequests cleaners ↔ g ∈ oracleRequests cleaners := by
  simp [mergedRequests]

theorem request_exclusion_membership (cleaners : List OwnedMark) (g : Nat) :
    g ∈ mergedRequests cleaners ↔ g ∈ excludedGroups cleaners := by
  simp [mergedRequests, oracleRequests, excludedGroups]

theorem every_cleaner_requested (cleaners : List OwnedMark) (t : OwnedMark)
    (ht : t ∈ cleaners) : t.group ∈ mergedRequests cleaners := by
  rw [request_exclusion_membership]
  exact List.mem_map.mpr ⟨t, ht, rfl⟩

theorem normal_record_sound (cleaners supplied : List OwnedMark) (m : OwnedMark)
    (h : m ∈ normalRecords cleaners supplied) : m ∈ exactTrace cleaners supplied := by
  simp only [normalRecords, List.mem_filter, decide_eq_true_eq] at h
  simp only [exactTrace, List.mem_filter, decide_eq_true_eq]
  refine ⟨h.1, ?_⟩
  intro hm
  exact h.2 (List.mem_map.mpr ⟨m, hm, rfl⟩)

theorem no_fabricated_repair (cleaners supplied : List OwnedMark) (m : OwnedMark)
    (h : m ∈ demandRepairs cleaners supplied) : m ∈ supplied := by
  exact (List.mem_filter.mp h).1

/-- An explicit executable witness chooses the normal or demand channel. -/
def routeWitness (cleaners : List OwnedMark) (m : OwnedMark) :
    Sum { _unit : Unit // m.group ∉ excludedGroups cleaners }
        { _unit : Unit // m.group ∈ mergedRequests cleaners } :=
  if h : m.group ∈ excludedGroups cleaners then
    .inr ⟨(), (request_exclusion_membership cleaners m.group).mpr h⟩
  else .inl ⟨(), h⟩

theorem normal_or_demand_exact_input (cleaners supplied : List OwnedMark) (m : OwnedMark) :
    (m ∈ normalRecords cleaners supplied ∨ m ∈ demandRepairs cleaners supplied) ↔
      m ∈ supplied := by
  constructor
  · intro h
    rcases h with hn | hd
    · exact (List.mem_filter.mp hn).1
    · exact (List.mem_filter.mp hd).1
  · intro h
    by_cases hg : m.group ∈ excludedGroups cleaners
    · right
      simp only [demandRepairs, List.mem_filter, decide_eq_true_eq]
      exact ⟨h, (request_exclusion_membership cleaners m.group).mpr hg⟩
    · left
      simp only [normalRecords, List.mem_filter, decide_eq_true_eq]
      exact ⟨h, hg⟩

theorem concrete_survivor_covered (cleaners supplied : List OwnedMark) (m : OwnedMark)
    (h : m ∈ exactTrace cleaners supplied) :
    m ∈ normalRecords cleaners supplied ∨ m ∈ demandRepairs cleaners supplied := by
  exact (normal_or_demand_exact_input cleaners supplied m).mpr (List.mem_filter.mp h).1

theorem excluded_query_needs_repair (cleaners supplied : List OwnedMark) (m : OwnedMark)
    (hs : m ∈ supplied) (hg : m.group ∈ excludedGroups cleaners) :
    m ∉ normalRecords cleaners supplied ∧ m ∈ demandRepairs cleaners supplied := by
  constructor
  · intro hn
    simp only [normalRecords, List.mem_filter, decide_eq_true_eq] at hn
    exact hn.2 hg
  · simp only [demandRepairs, List.mem_filter, decide_eq_true_eq]
    exact ⟨hs, (request_exclusion_membership cleaners m.group).mpr hg⟩

theorem merged_and_scalar_request_decode (cleaners supplied : List OwnedMark) (m : OwnedMark) :
    m ∈ demandRepairs cleaners supplied ↔
      m ∈ supplied ∧ m.group ∈ oracleRequests cleaners := by
  simp [demandRepairs, mergedRequests]

/-- A finite join oracle. The supplied list is the environment at the join,
    not a prescription to retain an AddedFactStore in the implementation. -/
def joinRequested (requests : List Nat) (supplied : List OwnedMark) : List OwnedMark :=
  supplied.filter (fun m => decide (m.group ∈ requests))

theorem supply_batch_distributes (requests : List Nat) (old fresh : List OwnedMark) :
    joinRequested requests (old ++ fresh) =
      joinRequested requests old ++ joinRequested requests fresh := by
  simp [joinRequested]

theorem request_batch_distributes (old fresh : List Nat)
    (supplied : List OwnedMark) (m : OwnedMark) :
    m ∈ joinRequested (old ++ fresh) supplied ↔
      m ∈ joinRequested old supplied ∨ m ∈ joinRequested fresh supplied := by
  simp only [joinRequested, List.mem_filter, decide_eq_true_eq, List.mem_append]
  constructor
  · rintro ⟨hm, hg | hg⟩
    · exact Or.inl ⟨hm, hg⟩
    · exact Or.inr ⟨hm, hg⟩
  · rintro (⟨hm, hg⟩ | ⟨hm, hg⟩)
    · exact ⟨hm, Or.inl hg⟩
    · exact ⟨hm, Or.inr hg⟩

theorem duplicate_interests_same_answers (requests : List Nat)
    (supplied : List OwnedMark) :
    joinRequested requests.eraseDups supplied = joinRequested requests supplied := by
  simp [joinRequested]

/-- The new-request route inspects existing supply; the new-supply route
    inspects existing requests. Both compute the same matching atom. -/
theorem both_arrival_routes (g : Nat) (m : OwnedMark) :
    materialize g [m] = joinRequested [g] [m] := by
  simp [materialize, joinRequested]

/- The two finite models use distinct record types. This executable bridge
   connects actual repaired members to complete, unchanged-payload B2 seeds. -/
namespace Handoff

def toFull (m : OwnedMark) : RuleGroupSchedule.FullMark := ⟨m.group, m.full⟩
def fromFull (m : RuleGroupSchedule.FullMark) : OwnedMark := ⟨m.group, m.identity⟩

@[simp] theorem owned_roundtrip (m : OwnedMark) : fromFull (toFull m) = m := by
  cases m
  rfl

@[simp] theorem full_roundtrip (m : RuleGroupSchedule.FullMark) : toFull (fromFull m) = m := by
  cases m
  rfl

def catalogView (catalog : List OwnedMark) : RuleGroupSchedule.Catalog := catalog.map toFull

/-- The returned query has an executable full-mark witness. No F1 full guide
    is required: catalog membership and the group demand suffice. -/
def fullQueryWitness (payload : RuleGroupSchedule.QueryPayload) (catalog : List OwnedMark)
    (m : OwnedMark) (hcat : m ∈ catalog) :
    { q : RuleGroupSchedule.FullQuery //
      q ∈ RuleGroupSchedule.extract (catalogView catalog) ⟨payload, m.group⟩ ∧
      q.mark = toFull m ∧ q.payload = payload } :=
  ⟨⟨payload, toFull m⟩,
    RuleGroupSchedule.extracted_complete ⟨payload, m.group⟩ (toFull m)
      (List.mem_map.mpr ⟨m, hcat, rfl⟩) rfl, rfl, rfl⟩

theorem repaired_member_decoded (payload : RuleGroupSchedule.QueryPayload)
    (cleaners supplied catalog : List OwnedMark) (m : OwnedMark)
    (_repair : m ∈ demandRepairs cleaners supplied) (hcat : m ∈ catalog) :
    (⟨payload, toFull m⟩ : RuleGroupSchedule.FullQuery) ∈
      RuleGroupSchedule.extract (catalogView catalog) ⟨payload, m.group⟩ :=
  (fullQueryWitness payload catalog m hcat).property.1

/-- Every exact survivor gets either the valid NORMAL restriction or a
    full-member next-run query. The catalog condition is a frontend contract,
    not the desired whole-engine reachability conclusion. -/
def survivorWitness (payload : RuleGroupSchedule.QueryPayload)
    (cleaners supplied catalog : List OwnedMark) (m : OwnedMark)
    (survives : m ∈ exactTrace cleaners supplied) (hcat : m ∈ catalog) :
    Sum { _unit : Unit // m ∈ normalRecords cleaners supplied }
        { q : RuleGroupSchedule.FullQuery //
          m ∈ demandRepairs cleaners supplied ∧
          q ∈ RuleGroupSchedule.extract (catalogView catalog) ⟨payload, m.group⟩ ∧
          q.mark = toFull m ∧ q.payload = payload } :=
  if hnormal : m ∈ normalRecords cleaners supplied then
    .inl ⟨(), hnormal⟩
  else
    let repair := (concrete_survivor_covered cleaners supplied m survives).resolve_left hnormal
    let query := fullQueryWitness payload catalog m hcat
    .inr ⟨query.val, repair, query.property⟩
end Handoff

namespace Controls
def t : OwnedMark := ⟨7, 10⟩
def u : OwnedMark := ⟨7, 11⟩
def v : OwnedMark := ⟨8, 20⟩

def sameGroupSurvivor : Bool :=
  decide (u ∈ exactTrace [t] [u] ∧
    u ∉ normalRecords [t] [u] ∧
    u ∈ demandRepairs [t] [u])

def cleaningSelectedMarkStaysDemand : Bool :=
  decide (t ∉ exactTrace [t] [t] ∧ t ∈ demandRepairs [t] [t])

def otherGroupStaysNormal : Bool :=
  decide (v ∈ normalRecords [t] [v] ∧ v ∉ demandRepairs [t] [v])

def requestsMerge : Bool := mergedRequests [t, u, t] == [7]

def lateActualSupply : Bool :=
  demandRepairs [t] [] == [] && demandRepairs [t] [u] == [u]

def cleanerTrace : Bool :=
  exactTrace [t, v] [t, u, v] == [u] &&
  demandRepairs [t, v] [t, u, v] == [t, u, v]

def handoffPayload : RuleGroupSchedule.QueryPayload := ⟨31, 45, [], [101]⟩

def fullQueryAfterRepair : Bool :=
  let q := Handoff.fullQueryWitness handoffPayload [t, u, v] u (by decide)
  decide (q.val.mark = Handoff.toFull u ∧ q.val.payload = handoffPayload)

def survivorChoosesDemand : Bool :=
  match Handoff.survivorWitness handoffPayload [t] [u] [t, u, v] u
      (by decide) (by decide) with
  | .inl _ => false
  | .inr q => decide (q.val.mark = Handoff.toFull u)

def controls : List (String × Bool) := [
  ("same group U survives cleaning T through demand", sameGroupSurvivor),
  ("cleaned exact T is only a demand false positive", cleaningSelectedMarkStaysDemand),
  ("another group remains in the normal restriction", otherGroupStaysNormal),
  ("repeated full marks make one group request", requestsMerge),
  ("request alone supplies no actual mark", lateActualSupply),
  ("cleaner sequence covers every concrete survivor", cleanerTrace),
  ("repair decodes full U with the unchanged payload", fullQueryAfterRepair),
  ("same-group survivor computes the demand-query route", survivorChoosesDemand)
]

theorem controls_pass : controls.all Prod.snd = true := by decide
end Controls
end ApSpec.RuleGroupRequestRepair
