CREATE INDEX IF NOT EXISTS "Classes_name" ON "Classes" ("name");

CREATE INDEX IF NOT EXISTS "Classes_outerMethodId" ON "Classes" ("outer_method");
CREATE INDEX IF NOT EXISTS "ClassInnerClasses_classId" ON "ClassInnerClasses" ("class_id");
CREATE INDEX IF NOT EXISTS "ClassInnerClasses_classId" ON "ClassInnerClasses" ("class_id");
CREATE INDEX IF NOT EXISTS "ClassInnerClasses_innerClassId" ON "ClassInnerClasses" ("inner_class_id");

CREATE INDEX IF NOT EXISTS "ClassHierarchies_classId" ON "ClassHierarchies" ("class_id");
CREATE INDEX IF NOT EXISTS "ClassHierarchies_superId" ON "ClassHierarchies" ("super_id");

CREATE INDEX IF NOT EXISTS "Annotations_classId" ON "Annotations" ("class_id");
CREATE INDEX IF NOT EXISTS "Annotations_fieldId" ON "Annotations" ("field_id");
CREATE INDEX IF NOT EXISTS "Annotations_methodId" ON "Annotations" ("method_id");
CREATE INDEX IF NOT EXISTS "Annotations_paramsId" ON "Annotations" ("param_id");

CREATE INDEX IF NOT EXISTS "Classes_location" ON "Classes" ("location_id");
CREATE INDEX IF NOT EXISTS "Fields_classId" ON "Fields" ("class_id");
CREATE INDEX IF NOT EXISTS "Methods_classId" ON "Methods" ("class_id");

CREATE INDEX IF NOT EXISTS "MethodParameters_methodId" ON "MethodParameters" ("method_id");

CREATE UNIQUE INDEX IF NOT EXISTS "Symbols_name" ON "Symbols" ("name");
CREATE UNIQUE INDEX IF NOT EXISTS "Bytecodelocations_hash" ON "BytecodeLocations" ("uniqueId");
CREATE UNIQUE INDEX IF NOT EXISTS "Methods_class_id_name_desc" ON "Methods" ("class_id", "name", "desc");
CREATE UNIQUE INDEX IF NOT EXISTS "Fields_class_id_name" ON "Fields" ("class_id", "name");
CREATE INDEX IF NOT EXISTS "Class Hierarchies" on "ClassHierarchies" ("super_id");

PRAGMA foreign_keys = ON;