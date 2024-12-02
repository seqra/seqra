CREATE TABLE IF NOT EXISTS "Calls"(
      "callee_class_symbol_id"      BIGINT NOT NULL,
      "callee_name_symbol_id"       BIGINT NOT NULL,
      "callee_desc_hash"            BIGINT,
      "opcode"                      INTEGER,
      "caller_class_symbol_id"      BIGINT NOT NULL,
      "caller_method_offsets"       BYTEA,
      "location_id"                 BIGINT NOT NULL
);