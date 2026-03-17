CREATE TABLE IF NOT EXISTS "Calls"(
      "callee_class_name"           VARCHAR(256) NOT NULL,
      "callee_name"                 VARCHAR(256) NOT NULL,
      "callee_desc_hash"            BIGINT,
      "opcode"                      INTEGER,
      "caller_class_name"           VARCHAR(256) NOT NULL,
      "caller_method_offsets"       BLOB,
      "location_id"                 BIGINT NOT NULL,
      CONSTRAINT "fk_location_id" FOREIGN KEY ("location_id") REFERENCES "BytecodeLocations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);