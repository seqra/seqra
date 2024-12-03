CREATE INDEX IF NOT EXISTS 'CallsSearch' ON Calls(opcode, location_id, callee_class_symbol_id, callee_name_symbol_id, callee_desc_hash);
