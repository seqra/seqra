CREATE INDEX IF NOT EXISTS 'CallsSearch' ON Calls(opcode, location_id, callee_class_name, callee_name, callee_desc_hash);
