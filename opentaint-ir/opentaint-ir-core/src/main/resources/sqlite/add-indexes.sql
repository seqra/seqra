CREATE INDEX IF NOT EXISTS "Classes_name" ON "Classes" ("name");
CREATE UNIQUE INDEX IF NOT EXISTS "Symbols_name" ON "Symbols" ("name");
CREATE UNIQUE INDEX IF NOT EXISTS "Bytecodelocations_hash" ON "BytecodeLocations" ("uniqueId");
CREATE UNIQUE INDEX IF NOT EXISTS "Methods_class_id_name_desc" ON "Methods" ("class_id", "name", "desc");
CREATE UNIQUE INDEX IF NOT EXISTS "Fields_class_id_name" ON "Fields" ("class_id", "name");
CREATE INDEX IF NOT EXISTS "Class Hierarchies" on "ClassHierarchies" ("super_id");

PRAGMA foreign_keys = ON;