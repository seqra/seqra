/- Interactive finite replay from localLaw provider correspondence. Private cache
states can differ. This theorem requires equality of complete rule lists and
related logical provider state for each admitted query; it does not assert
that Kotlin adapters satisfy those premises. -/
import Std
namespace ApSpec.RuleProviderReplay
set_option autoImplicit false
universe u v w x y

def execute {Engine : Type u} {Provider : Type v} {Query : Type w} {Row : Type x}
    (next : Engine → Option Query)
    (applyRows : Engine → Query → List Row → Engine)
    (answer : Query → Provider → List Row × Provider) :
    Nat → Engine → Provider → Engine × Provider
  | 0, engine, provider => (engine, provider)
  | fuel + 1, engine, provider =>
      match next engine with
      | none => (engine, provider)
      | some query =>
          let result := answer query provider
          execute next applyRows answer fuel (applyRows engine query result.1) result.2

/-- The query is chosen by the evolving engine, not by a fixed old query tape. -/
theorem interactive_replay_equal
    {Engine : Type u} {Left : Type v} {Right : Type y} {Query : Type w} {Row : Type x}
    (next : Engine → Option Query) (applyRows : Engine → Query → List Row → Engine)
    (old : Query → Left → List Row × Left) (new : Query → Right → List Row × Right)
    (related : Left → Right → Prop)
    (localLaw : ∀ query left right, related left right →
      (old query left).1 = (new query right).1 ∧
      related (old query left).2 (new query right).2)
    (fuel : Nat) (engine : Engine) (left : Left) (right : Right)
    (start : related left right) :
    (execute next applyRows old fuel engine left).1 =
      (execute next applyRows new fuel engine right).1 ∧
    related (execute next applyRows old fuel engine left).2
      (execute next applyRows new fuel engine right).2 := by
  induction fuel generalizing engine left right with
  | zero => exact ⟨rfl, start⟩
  | succ fuel ih =>
      cases h : next engine with
      | none => simpa [execute,h] using (And.intro (Eq.refl engine) start)
      | some query =>
          have same := localLaw query left right start
          have advance := ih (applyRows engine query (old query left).1)
            (old query left).2 (new query right).2 same.2
          simpa only [execute,h, same.1] using advance

theorem guidance_equal
    {Engine : Type u} {Left : Type v} {Right : Type y} {Query : Type w}
    {Row : Type x} {Guide : Type}
    (next : Engine → Option Query) (applyRows : Engine → Query → List Row → Engine)
    (old : Query → Left → List Row × Left) (new : Query → Right → List Row × Right)
    (related : Left → Right → Prop)
    (localLaw : ∀ query left right, related left right →
      (old query left).1 = (new query right).1 ∧
      related (old query left).2 (new query right).2)
    (fuel : Nat) (engine : Engine) (left : Left) (right : Right)
    (start : related left right) (handoff : Engine → List Guide) :
    handoff (execute next applyRows old fuel engine left).1 =
      handoff (execute next applyRows new fuel engine right).1 := by
  exact congrArg handoff
    (interactive_replay_equal next applyRows old new related localLaw fuel engine left right start).1

namespace Controls
structure Provider where
  counter : Nat
  cache : List Nat
  deriving DecidableEq, Repr

def old (query : Nat) (s : Provider) : List Nat × Provider :=
  ([query + s.counter], ⟨s.counter + 1, query :: 999 :: s.cache⟩)
def new (query : Nat) (s : Provider) : List Nat × Provider :=
  ([query + s.counter], ⟨s.counter + 1, query :: s.cache⟩)
def related (left right : Provider) : Prop := left.counter = right.counter

theorem local_equal (query : Nat) (left right : Provider) (h : related left right) :
    (old query left).1 = (new query right).1 ∧
    related (old query left).2 (new query right).2 := by
  constructor
  · simp [old,new,related] at *; exact h
  · simp [old,new,related] at *; exact h

def next (engine : Nat) : Option Nat := if engine < 8 then some engine else none
def applyRows (engine query : Nat) (rows : List Nat) : Nat := engine + query + rows.length + 1
def initial : Provider := ⟨0,[]⟩
def controls : List Bool := [
  decide ((execute next applyRows old 0 0 initial).1 = 0),
  decide ((execute next applyRows old 1 0 initial).1 = 2),
  decide ((execute next applyRows old 2 0 initial).1 = 6),
  decide ((execute next applyRows old 3 0 initial).1 = 14),
  decide ((execute next applyRows old 8 0 initial).1 =
          (execute next applyRows new 8 0 initial).1),
  decide ((execute next applyRows old 8 0 initial).2.counter = 3),
  decide ((execute next applyRows old 8 0 initial).2.cache ≠
          (execute next applyRows new 8 0 initial).2.cache)
]
theorem controls_pass : controls.all id = true := by decide
end Controls
end ApSpec.RuleProviderReplay
