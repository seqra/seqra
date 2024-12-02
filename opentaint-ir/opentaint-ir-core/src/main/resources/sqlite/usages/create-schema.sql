CREATE TABLE IF NOT EXISTS "Calls"(
      "callee_class_symbol_id"      BIGINT NOT NULL,
      "callee_name_symbol_id"       BIGINT NOT NULL,
      "callee_desc_hash"            BIGINT,
      "opcode"                      INTEGER,
      "caller_class_symbol_id"      BIGINT NOT NULL,
      "caller_method_offsets"       BLOB,
      "location_id"                 BIGINT NOT NULL,
      CONSTRAINT "fk_callee_class_symbol_id" FOREIGN KEY ("callee_class_symbol_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE,
      CONSTRAINT "fk_location_id" FOREIGN KEY ("location_id") REFERENCES "BytecodeLocations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);