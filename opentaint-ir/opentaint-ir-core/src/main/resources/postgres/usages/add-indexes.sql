SELECT CREATE_CONSTRAINT('Calls', 'fk_callee_class_symbol_id', 'ALTER TABLE "Calls" ADD CONSTRAINT "fk_callee_class_symbol_id" FOREIGN KEY ("callee_class_symbol_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE');
SELECT CREATE_CONSTRAINT('Calls', 'fk_location_id', 'ALTER TABLE "Calls" ADD CONSTRAINT "fk_location_id" FOREIGN KEY ("location_id") REFERENCES "BytecodeLocations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

CREATE INDEX IF NOT EXISTS "CallsSearch" ON "Calls"(opcode, location_id, callee_class_symbol_id, callee_name_symbol_id, callee_desc_hash)
