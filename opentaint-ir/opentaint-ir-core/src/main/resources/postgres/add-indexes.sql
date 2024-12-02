SELECT CREATE_CONSTRAINT('Classes', 'fk_Classes_outer_method__id', 'ALTER TABLE "Classes" ADD CONSTRAINT "fk_Classes_outer_method__id" FOREIGN KEY ("outer_method") REFERENCES "Methods" (id) ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Classes', 'fk_Classes_name__id', 'ALTER TABLE "Classes" ADD CONSTRAINT "fk_Classes_name__id" FOREIGN KEY ("name") REFERENCES "Symbols" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Classes', 'fk_Classes_package_id__id', 'ALTER TABLE "Classes" ADD CONSTRAINT "fk_Classes_package_id__id" FOREIGN KEY ("package_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Classes', 'fk_Classes_location_id__id', 'ALTER TABLE "Classes" ADD CONSTRAINT "fk_Classes_location_id__id" FOREIGN KEY ("location_id") REFERENCES "BytecodeLocations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Classes', 'fk_Classes_outer_class__id', 'ALTER TABLE "Classes" ADD CONSTRAINT "fk_Classes_outer_class__id" FOREIGN KEY ("outer_class") REFERENCES "OuterClasses" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

SELECT CREATE_CONSTRAINT('Methods', 'fk_Methods_class_id__id', 'ALTER TABLE "Methods" ADD CONSTRAINT "fk_Methods_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Methods', 'fk_Methods_name__id', 'ALTER TABLE "Methods" ADD CONSTRAINT "fk_Methods_name__id" FOREIGN KEY ("name") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Methods', 'fk_Methods_return_class__id', 'ALTER TABLE "Methods" ADD CONSTRAINT "fk_Methods_return_class__id" FOREIGN KEY ("return_class") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

SELECT CREATE_CONSTRAINT('ClassHierarchies', 'fk_ClassHierarchies_super_id__id', 'ALTER TABLE "ClassHierarchies" ADD CONSTRAINT "fk_ClassHierarchies_super_id__id" FOREIGN KEY ("super_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('ClassHierarchies', 'fk_ClassHierarchies_class_id__id', 'ALTER TABLE "ClassHierarchies" ADD CONSTRAINT "fk_ClassHierarchies_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

SELECT CREATE_CONSTRAINT('OuterClasses', 'fk_OuterClasses_outer_class_name_id__id', 'ALTER TABLE "OuterClasses" ADD CONSTRAINT "fk_OuterClasses_outer_class_name_id__id" FOREIGN KEY ("outer_class_name_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

SELECT CREATE_CONSTRAINT('ClassInnerClasses', 'fk_ClassInnerClasses_inner_class_id__id', 'ALTER TABLE "ClassInnerClasses" ADD CONSTRAINT "fk_ClassInnerClasses_inner_class_id__id" FOREIGN KEY ("inner_class_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('ClassInnerClasses', 'fk_ClassInnerClasses_class_id__id', 'ALTER TABLE "ClassInnerClasses" ADD CONSTRAINT "fk_ClassInnerClasses_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

SELECT CREATE_CONSTRAINT('MethodParameters', 'fk_MethodParameters_method_id__id', 'ALTER TABLE "MethodParameters" ADD CONSTRAINT "fk_MethodParameters_method_id__id" FOREIGN KEY ("method_id") REFERENCES "Methods" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('MethodParameters', 'fk_MethodParameters_parameter_class__id', 'ALTER TABLE "MethodParameters" ADD CONSTRAINT "fk_MethodParameters_parameter_class__id" FOREIGN KEY ("parameter_class") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

SELECT CREATE_CONSTRAINT('Fields', 'fk_Fields_name__id', 'ALTER TABLE "Fields" ADD CONSTRAINT "fk_Fields_name__id" FOREIGN KEY ("name") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Fields', 'fk_Fields_field_class__id', 'ALTER TABLE "Fields" ADD CONSTRAINT "fk_Fields_field_class__id" FOREIGN KEY ("field_class") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Fields', 'fk_Fields_class_id__id', 'ALTER TABLE "Fields" ADD CONSTRAINT "fk_Fields_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

SELECT CREATE_CONSTRAINT('Annotations', 'fk_Annotations_param_id__id', 'ALTER TABLE "Annotations" ADD CONSTRAINT "fk_Annotations_param_id__id" FOREIGN KEY ("param_id") REFERENCES "MethodParameters" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Annotations', 'fk_Annotations_class_id__id', 'ALTER TABLE "Annotations" ADD CONSTRAINT "fk_Annotations_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Annotations', 'fk_Annotations_field_id__id', 'ALTER TABLE "Annotations" ADD CONSTRAINT "fk_Annotations_field_id__id" FOREIGN KEY ("field_id") REFERENCES "Fields" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Annotations', 'fk_Annotations_annotation_name__id', 'ALTER TABLE "Annotations" ADD CONSTRAINT "fk_Annotations_annotation_name__id" FOREIGN KEY ("annotation_name") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Annotations', 'fk_Annotations_method_id__id', 'ALTER TABLE "Annotations" ADD CONSTRAINT "fk_Annotations_method_id__id" FOREIGN KEY ("method_id") REFERENCES "Methods" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('Annotations', 'fk_Annotations_parent_annotation__id', 'ALTER TABLE "Annotations" ADD CONSTRAINT "fk_Annotations_parent_annotation__id" FOREIGN KEY ("parent_annotation") REFERENCES "Annotations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

SELECT CREATE_CONSTRAINT('AnnotationValues', 'fk_AnnotationValues_annotation_id__id', 'ALTER TABLE "AnnotationValues" ADD CONSTRAINT "fk_AnnotationValues_annotation_id__id" FOREIGN KEY ("annotation_id") REFERENCES "Annotations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('AnnotationValues', 'fk_AnnotationValues_enum_value__id', 'ALTER TABLE "AnnotationValues" ADD CONSTRAINT "fk_AnnotationValues_enum_value__id" FOREIGN KEY ("enum_value") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('AnnotationValues', 'fk_AnnotationValues_ref_annotation_id__id', 'ALTER TABLE "AnnotationValues" ADD CONSTRAINT "fk_AnnotationValues_ref_annotation_id__id" FOREIGN KEY ("ref_annotation_id") REFERENCES "Annotations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');
SELECT CREATE_CONSTRAINT('AnnotationValues', 'fk_AnnotationValues_class_symbol__id', 'ALTER TABLE "AnnotationValues" ADD CONSTRAINT "fk_AnnotationValues_class_symbol__id" FOREIGN KEY ("class_symbol") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT');

CREATE INDEX IF NOT EXISTS "Classes_name" ON "Classes" ("name");
CREATE UNIQUE INDEX IF NOT EXISTS "Symbols_name" ON "Symbols" ("name");
CREATE UNIQUE INDEX IF NOT EXISTS "Bytecodelocations_hash" ON "BytecodeLocations" ("uniqueId");
CREATE UNIQUE INDEX IF NOT EXISTS "Methods_class_id_name_desc" ON "Methods" ("class_id", "name", "desc");
CREATE UNIQUE INDEX IF NOT EXISTS "Fields_class_id_name" ON "Fields" ("class_id", "name");
CREATE INDEX IF NOT EXISTS "Class Hierarchies" on "ClassHierarchies" ("super_id");
