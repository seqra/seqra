/- Lossless static call-input coding, whole-premise joins and persistence.
   All codecs are executable. No assumed optimization equivalence. -/
import ApSpec.Run1Requests
import ApSpec.Run1RequestProtocol
namespace ApSpec.BottomUpStaticProtocol
open ApSpec

structure Port where
  caller : Run1Requests.Owner
  call : Nat
  callee : Run1Requests.Owner
  stage : Nat
  callerDemand : Bool
  deriving DecidableEq, Repr
inductive Origin where
  | zero
  | flow (slot : Run1Requests.Slot)
  | concrete (members : List PFact)
  deriving DecidableEq, Repr
structure Input where
  port : Port
  origin : Origin
  current : AFact
  deriving DecidableEq, Repr
structure IdentityToken where
  port : Port
  cls : Nat
  field : Nat
  deriving DecidableEq, Repr

def canonical (c f : Nat) : AFact :=
  ⟨(Run1Requests.Slot.field c f).initial, true⟩
def IdentityToken.decode (t : IdentityToken) : Input :=
  ⟨t.port,.flow (.field t.cls t.field),canonical t.cls t.field⟩
inductive Encoded where
  | identity (token : IdentityToken)
  | bound (input : Input)
  deriving DecidableEq, Repr

def encode (i : Input) : Encoded :=
  match i.origin with
  | .flow (.field c f) =>
    if i.current = canonical c f then .identity ⟨i.port,c,f⟩ else .bound i
  | _ => .bound i

def decode : Encoded → Input
  | .identity t => t.decode
  | .bound i => i

theorem decode_encode (i : Input) : decode (encode i) = i := by
  cases i with
  | mk port origin current =>
    cases origin with
    | zero => rfl
    | concrete members => rfl
    | flow slot =>
      cases slot with
      | base b => rfl
      | field c f =>
        by_cases h : current = canonical c f
        · subst current; simp [encode,decode,IdentityToken.decode]
        · simp [encode,decode,h]

/-- Any ordinary prefix/call/exit operator receives its original FULL input. -/
theorem interpreter_preserved {Result : Type} (run : Input → Result) (i : Input) :
    run (decode (encode i)) = run i := by rw [decode_encode]
theorem port_preserved (i : Input) : (decode (encode i)).port = i.port := by rw [decode_encode]
theorem origin_preserved (i : Input) : (decode (encode i)).origin = i.origin := by rw [decode_encode]
theorem payload_preserved (i : Input) : (decode (encode i)).current = i.current := by rw [decode_encode]
theorem encoded_inputs_exact (inputs : List Input) :
    (inputs.map encode).map decode = inputs := by
  induction inputs with
  | nil => rfl
  | cons i inputs ih => simp [decode_encode,ih]
theorem transformed_stays_bound (p : Port) (c f : Nat) (a : AFact)
    (h : a ≠ canonical c f) :
    encode ⟨p,.flow (.field c f),a⟩ = .bound ⟨p,.flow (.field c f),a⟩ := by simp [encode,h]
theorem concrete_stays_bound (p : Port) (members : List PFact) (a : AFact) :
    encode ⟨p,.concrete members,a⟩ = .bound ⟨p,.concrete members,a⟩ := rfl

/-- Enumerates whole N-ary combinations, never one implication per member. -/
def products {α : Type} : List (List α) → List (List α)
  | [] => [[]]
  | row :: rows => row.flatMap fun x => (products rows).map (x :: ·)
inductive Selected {α : Type} : List (List α) → List α → Prop
  | nil : Selected [] []
  | cons {row rows x xs} (head : x ∈ row) (tail : Selected rows xs) :
      Selected (row :: rows) (x :: xs)
theorem selected_length {α : Type} {rows : List (List α)} {xs : List α}
    (h : Selected rows xs) : xs.length = rows.length := by
  induction h with
  | nil => rfl
  | cons _ _ ih => simp [ih]
theorem mem_products_iff {α : Type} (rows : List (List α)) (xs : List α) :
    xs ∈ products rows ↔ Selected rows xs := by
  induction rows generalizing xs with
  | nil =>
    constructor
    · intro h
      have hx : xs = [] := by simpa [products] using h
      subst xs; exact .nil
    · intro h; cases h; simp [products]
  | cons row rows ih =>
    constructor
    · intro h
      obtain ⟨x,hx,rest⟩ := List.mem_flatMap.mp h
      obtain ⟨tail,ht,eq⟩ := List.mem_map.mp rest
      subst xs
      exact .cons hx ((ih tail).mp ht)
    · intro h
      cases h with
      | cons hx ht =>
        exact List.mem_flatMap.mpr ⟨_,hx,List.mem_map.mpr ⟨_,(ih _).mpr ht,rfl⟩⟩
theorem product_has_all_members {α : Type} {rows : List (List α)} {xs : List α}
    (h : xs ∈ products rows) : xs.length = rows.length :=
  selected_length ((mem_products_iff _ _).mp h)
theorem empty_member_no_combination {α : Type} (before after : List (List α)) :
    products (before ++ [] :: after) = [] := by
  induction before with
  | nil => simp [products]
  | cons row rows ih => simp [products,ih]

/-- Literal admitLiteral stay ordinary; full call/context identity is enforced here. -/
def matchedRows (port : Port) (literals : List PFact) (inputs : List Input)
    (admitLiteral : PFact → Input → Bool) : List (List Input) :=
  literals.map fun literal => inputs.filter fun i => decide (i.port = port) && admitLiteral literal i
def joinedInputs (port : Port) (literals : List PFact) (inputs : List Input)
    (admitLiteral : PFact → Input → Bool) : List (List Input) :=
  products (matchedRows port literals inputs admitLiteral)
theorem rows_scope (port : Port) (literal : PFact) (inputs : List Input)
    (admitLiteral : PFact → Input → Bool) (i : Input)
    (h : i ∈ inputs.filter (fun a => decide (a.port = port) && admitLiteral literal a)) :
    i ∈ inputs ∧ i.port = port ∧ admitLiteral literal i = true := by
  have pair := List.mem_filter.mp h
  have both := (Bool.and_eq_true _ _).mp pair.2
  exact ⟨pair.1,of_decide_eq_true both.1,both.2⟩
theorem joined_arity (port : Port) (literals : List PFact) (inputs : List Input)
    (admitLiteral : PFact → Input → Bool) (xs : List Input)
    (h : xs ∈ joinedInputs port literals inputs admitLiteral) : xs.length = literals.length := by
  have result := product_has_all_members h
  simpa [matchedRows] using result
theorem joined_codec_equal (port : Port) (literals : List PFact) (inputs : List Input)
    (admitLiteral : PFact → Input → Bool) :
    joinedInputs port literals ((inputs.map encode).map decode) admitLiteral =
      joinedInputs port literals inputs admitLiteral := by rw [encoded_inputs_exact]

structure Summary where
  owner : Run1Requests.Owner
  origin : Origin
  current : AFact
  deriving DecidableEq, Repr
def pureIdentity (s : Summary) : Bool :=
  match s.origin with
  | .flow (.field c f) => decide (s.current = canonical c f)
  | _ => false
def compact (rows : List Summary) : List Summary := rows.filter fun s => !pureIdentity s
def persist (eligible : Summary → Bool) (rows : List Summary) : List Summary :=
  rows.filter fun s => !s.current.demand && eligible s
theorem pure_identity_demand (s : Summary) (h : pureIdentity s = true) :
    s.current.demand = true := by
  cases s with
  | mk owner origin current =>
    cases origin with
    | zero => simp [pureIdentity] at h
    | concrete members => simp [pureIdentity] at h
    | flow slot =>
      cases slot with
      | base b => simp [pureIdentity] at h
      | field c f =>
        have eq : current = canonical c f := of_decide_eq_true h
        rw [eq]; rfl
theorem normal_not_compacted (s : Summary) (h : s.current.demand = false) :
    pureIdentity s = false := by
  cases identity : pureIdentity s with
  | false => rfl
  | true =>
    have d := pure_identity_demand s identity
    rw [h] at d; cases d
theorem persistence_compaction_equal (eligible : Summary → Bool) (rows : List Summary) :
    persist eligible (compact rows) = persist eligible rows := by
  unfold persist compact
  rw [List.filter_filter]
  apply List.filter_congr
  intro s _
  cases id : pureIdentity s with
  | false => simp
  | true =>
    have d := pure_identity_demand s id
    simp [d]

def rootDefault (actual : AFact) : AFact := ⟨actual.fact,true⟩
theorem root_default_payload (a : AFact) : (rootDefault a).fact = a.fact := rfl
theorem root_default_demand (a : AFact) : (rootDefault a).demand = true := rfl
theorem field_identity_covered (c f : Nat) : rootDefault (canonical c f) = canonical c f := rfl
theorem normal_persistence (eligible : Summary → Bool) (s : Summary)
    (normal : s.current.demand = false) (ok : eligible s = true) :
    persist eligible [s] = [s] := by simp [persist,normal,ok]
theorem demand_not_persisted (eligible : Summary → Bool) (s : Summary)
    (demand : s.current.demand = true) : persist eligible [s] = [] := by simp [persist,demand]

/- The diagnostic-policy question has an executable countermodel. Two valid
   batching schedules have the same final cells but different growth callbacks.
   These are observation counters, not an analyzer implementation change. -/
structure Diagnostic where
  admitted : List Nat
  callbacks : Nat
  deriving DecidableEq, Repr

def diagnosticBatch (state : Diagnostic) (cells : List Nat) : Diagnostic :=
  let fresh := Run1RequestProtocol.difference cells state.admitted
  ⟨Run1RequestProtocol.merge cells state.admitted,
    state.callbacks + if fresh.isEmpty then 0 else 1⟩

def packedDiagnostic : Diagnostic := diagnosticBatch ⟨[],0⟩ [1,2]
def splitDiagnostic : Diagnostic := diagnosticBatch (diagnosticBatch ⟨[],0⟩ [1]) [2]

theorem diagnostic_final_cells_equal (cell : Nat) :
    cell ∈ packedDiagnostic.admitted ↔ cell ∈ splitDiagnostic.admitted := by
  simp [packedDiagnostic,splitDiagnostic,diagnosticBatch,or_comm]

theorem diagnostic_counts_differ :
    packedDiagnostic.callbacks = 1 ∧ splitDiagnostic.callbacks = 2 := by decide

namespace Controls
 def port : Port := ⟨⟨1,2,[3]⟩,4,⟨5,6,[7]⟩,8,false⟩
 def input : Input := ⟨port,.flow (.field 2 3),canonical 2 3⟩
 def otherPort : Port := {port with call := 9}
 def output : PFact := ⟨9,[],.exact,.conc 7⟩
 def a : Input := ⟨port,.concrete [output],⟨output,false⟩⟩
 def b : Input := ⟨otherPort,.concrete [output],⟨output,false⟩⟩
 #guard encode input = .identity ⟨port,2,3⟩
 #guard decode (encode input) = input
 #guard products ([[1],[]] : List (List Nat)) = []
 #guard products ([[1],[2]] : List (List Nat)) = [[1,2]]
 #guard joinedInputs port [output,output] [a,b] (fun _ _ => true) = [[a,a]]
 #guard joinedInputs port [output] [b] (fun _ _ => true) = []
 #guard persist (fun _ => true) [⟨port.caller,input.origin,input.current⟩] = []
 #guard persist (fun _ => true) [⟨port.caller,a.origin,a.current⟩] = [⟨port.caller,a.origin,a.current⟩]
 def checks : List Bool := [
   encode input == .identity ⟨port,2,3⟩,
   decode (encode input) == input,
   products ([[1],[]] : List (List Nat)) == [],
   products ([[1],[2]] : List (List Nat)) == [[1,2]],
   joinedInputs port [output,output] [a,b] (fun _ _ => true) == [[a,a]],
   joinedInputs port [output] [b] (fun _ _ => true) == [],
   persist (fun _ => true) [⟨port.caller,input.origin,input.current⟩] == [],
   persist (fun _ => true) [⟨port.caller,a.origin,a.current⟩] == [⟨port.caller,a.origin,a.current⟩],
   packedDiagnostic.admitted.all (fun cell => splitDiagnostic.admitted.contains cell),
   packedDiagnostic.callbacks == 1 && splitDiagnostic.callbacks == 2]
 theorem controls_pass : checks.all id = true := by decide
end Controls
end ApSpec.BottomUpStaticProtocol
