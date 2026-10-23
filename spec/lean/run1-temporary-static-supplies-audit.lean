import ApSpec.Run1TemporaryStaticSupplies
import Lean

open Lean Elab Command

run_elab do
  let owned := `ApSpec.Run1TemporaryStaticSupplies
  let env ← getEnv
  let mut count : Nat := 0
  for (name, _) in env.constants.toList do
    let module := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]!
      | none => Name.anonymous
    if module == owned then
      count := count + 1
      let dependencies ← collectAxioms name
      let forbidden := dependencies.filter (fun n => n != `propext && n != `Quot.sound)
      if !forbidden.isEmpty then
        throwError "Unexpected axioms: {name}: {forbidden}"
  if count == 0 then throwError "No owned declarations audited"
  logInfo m!"Audited {owned}: {count} declarations; no unexpected axioms."

namespace SupplyAudit
open ApSpec.Run1TemporaryStaticSupplies
open ApSpec.Run1TemporaryStaticSupplies.Controls

def spatial : SpatialAdapter := ⟨fun reader region =>
  if reader = 23 ∧ region.slot = fieldAny.slot then some region else none⟩

def initial : Service := {
  atoms := fun key =>
    if key = (fieldAny.slot,t) ∨ key = (fieldAny.slot,u)
      then (({} : AtomFrame).request 17).writer 7 else {}
  reads := fun key => if key.1 = 23 then ({} : ReadCell).register else {}
}

def result : Service := executeForward (deliver spatial) (decode (merge [a,c,b,a])) initial

def controls : List Bool := [
  (result.atoms (fieldAny.slot,t)).live.membership.known,
  (result.atoms (fieldAny.slot,u)).live.membership.known,
  (result.atoms (fieldAny.slot,t)).answers 17,
  (result.atoms (fieldAny.slot,u)).answers 17,
  !(result.atoms (fieldAny.slot,t)).live.pending 17,
  !(result.atoms (fieldAny.slot,u)).live.pending 17,
  (result.atoms (fieldAny.slot,t)).live.admitted 7,
  (result.atoms (fieldAny.slot,t)).writerEffects 7,
  (result.reads (23,fieldAny)).delivered,
  (result.reads (23,fieldExact)).delivered,
  !(result.reads (24,fieldAny)).delivered,
  !(result.atoms (otherField.slot,t)).live.membership.known,
  !(result.atoms (marker.slot,t)).live.membership.known,
  !(result.atoms (fieldAny.slot,⟨7,12⟩)).live.membership.known,
  let grouped := executeForward (deliverPacket spatial)
    (mergedPackets [⟨fieldAny,[t,u]⟩,⟨fieldExact,[t]⟩,⟨fieldAny,[t]⟩]) initial
  (grouped.atoms (fieldAny.slot,t)).answers 17,
  let grouped := executeForward (deliverPacket spatial)
    (mergedPackets [⟨fieldAny,[t,u]⟩,⟨fieldExact,[t]⟩,⟨fieldAny,[t]⟩]) initial
  (grouped.atoms (fieldAny.slot,u)).answers 17,
  let empty := deliverPacket spatial ⟨fieldAny,[]⟩ initial
  !(empty.atoms (fieldAny.slot,t)).live.membership.known,
  let empty := deliverPacket spatial ⟨fieldAny,[]⟩ initial
  !(empty.reads (23,fieldAny)).delivered,
  decide (replace (.canonical (merge [a,b,a])) = .canonical []),
  decide (replace (.ordered [⟨rawP,t⟩,⟨rawQ,u⟩]) = .ordered []),
  let b : Barrier := ⟨.canonicalRun1Full,false,false,false,true⟩
  (replaceBarrier (commitBarrier b true)).counted,
  let b : Barrier := ⟨.canonicalRun1Full,true,true,false,true⟩
  (releaseBarrier b).counted,
  let b : Barrier := ⟨.canonicalRun1Full,true,false,true,true⟩
  (releaseBarrier b).counted,
  let b : Barrier := ⟨.canonicalRun1Full,true,true,true,true⟩
  !(releaseBarrier b).counted
]
end SupplyAudit

#eval do
  let checks := ApSpec.Run1TemporaryStaticSupplies.Controls.controls ++ SupplyAudit.controls
  if !(checks.all id) then throw (IO.userError "Temporary supply control failed")
  IO.println s!"Temporary supply executable controls: {checks.length}; all passed."

#print axioms ApSpec.Run1TemporaryStaticSupplies.decoded_exact
#print axioms ApSpec.Run1TemporaryStaticSupplies.decoded_no_duplicates
#print axioms ApSpec.Run1TemporaryStaticSupplies.membership_cost_never_grows
#print axioms ApSpec.Run1TemporaryStaticSupplies.repeated_work_strictly_reduced
#print axioms ApSpec.Run1TemporaryStaticSupplies.deliveries_commute
#print axioms ApSpec.Run1TemporaryStaticSupplies.packet_atomic_correspondence
#print axioms ApSpec.Run1TemporaryStaticSupplies.merged_packet_commit_equal
#print axioms ApSpec.Run1TemporaryStaticSupplies.canonical_supply_commit_equal
#print axioms ApSpec.Run1TemporaryStaticSupplies.whole_commit_equal
#print axioms ApSpec.Run1TemporaryStaticSupplies.raw_first_match_counterexample
#print axioms ApSpec.Run1TemporaryStaticSupplies.counted_commit_sequence
#print axioms ApSpec.Run1TemporaryStaticSupplies.query_demand_witness
