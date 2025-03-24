CREATE TABLE IF NOT EXISTS "Applicationmetadata"(
    version VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS "Refactorings"(
    name VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS "BytecodeLocations"(
    "id"         BIGINT PRIMARY KEY,
    "path"       VARCHAR(1024) NOT NULL,
    "uniqueId"       VARCHAR(1024) NOT NULL,
    "runtime"    BOOLEAN       NOT NULL DEFAULT 0,
    "state"      INT           NOT NULL DEFAULT 0,
    "updated_id" BIGINT,
    CONSTRAINT "fk_BytecodeLocations_updated_id__id" FOREIGN KEY ("updated_id") REFERENCES "BytecodeLocations" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS "Symbols"(
    "id"   BIGINT PRIMARY KEY,
    "name" VARCHAR(256) NOT NULL
);

CREATE TABLE IF NOT EXISTS "OuterClasses"(
    "id"                  BIGINT PRIMARY KEY,
    "outer_class_name_id" BIGINT NOT NULL,
    "name"                VARCHAR(256),
    "method_name"         TEXT,
    "method_desc"         TEXT,
    CONSTRAINT "fk_OuterClasses_outer_class_name_id__id" FOREIGN KEY ("outer_class_name_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS "Methods"(
    "id"           BIGINT PRIMARY KEY,
    "access"       INT    NOT NULL,
    "name"         BIGINT NOT NULL,
    "signature"    TEXT,
    "desc"         TEXT,
    "return_class" BIGINT,
    "class_id"     BIGINT NOT NULL,
    CONSTRAINT "fk_Methods_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Methods_name__id" FOREIGN KEY ("name") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Methods_return_class__id" FOREIGN KEY ("return_class") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS "Classes"(
    "id"           BIGINT PRIMARY KEY,
    "access"       INT    NOT NULL,
    "name"         BIGINT NOT NULL,
    "signature"    TEXT,
    "bytecode"     BLOB   NOT NULL,
    "location_id"  BIGINT NOT NULL,
    "package_id"   BIGINT NOT NULL,
    "outer_class"  BIGINT,
    "outer_method" BIGINT,
    CONSTRAINT "fk_Classes_outer_method__id" FOREIGN KEY ("outer_method") REFERENCES "Methods" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Classes_name__id" FOREIGN KEY ("name") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Classes_package_id__id" FOREIGN KEY ("package_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Classes_location_id__id" FOREIGN KEY ("location_id") REFERENCES "BytecodeLocations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Classes_outer_class__id" FOREIGN KEY ("outer_class") REFERENCES "OuterClasses" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS "ClassHierarchies"(
    "id"           BIGINT PRIMARY KEY,
    "class_id"     BIGINT  NOT NULL,
    "super_id"     BIGINT  NOT NULL,
    "is_class_ref" BOOLEAN NOT NULL,
    CONSTRAINT "fk_ClassHierarchies_super_id__id" FOREIGN KEY ("super_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_ClassHierarchies_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS "ClassInnerClasses"(
    "id"             BIGINT PRIMARY KEY,
    "class_id"       BIGINT NOT NULL,
    "inner_class_id" BIGINT NOT NULL,
    CONSTRAINT "fk_ClassInnerClasses_inner_class_id__id" FOREIGN KEY ("inner_class_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_ClassInnerClasses_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS "MethodParameters"(
    "id"              BIGINT PRIMARY KEY,
    "access"          INT    NOT NULL,
    "index"           INT    NOT NULL,
    "name"            VARCHAR(256),
    "parameter_class" BIGINT NOT NULL,
    "method_id"       BIGINT NOT NULL,
    CONSTRAINT "fk_MethodParameters_method_id__id" FOREIGN KEY ("method_id") REFERENCES "Methods" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_MethodParameters_parameter_class__id" FOREIGN KEY ("parameter_class") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS "Fields"(
    "id"          BIGINT PRIMARY KEY,
    "access"      INT    NOT NULL,
    "name"        BIGINT NOT NULL,
    "signature"   TEXT,
    "field_class" BIGINT NOT NULL,
    "class_id"    BIGINT NOT NULL,
    CONSTRAINT "fk_Fields_name__id" FOREIGN KEY ("name") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Fields_field_class__id" FOREIGN KEY ("field_class") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Fields_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS "Annotations"(
    "id"                BIGINT PRIMARY KEY,
    "annotation_name"   BIGINT  NOT NULL,
    "visible"           BOOLEAN NOT NULL,
    "type_reference"    BIGINT,
    "type_path"         VARCHAR(256),
    "parent_annotation" BIGINT,
    "class_id"          BIGINT,
    "method_id"         BIGINT,
    "field_id"          BIGINT,
    "param_id"          BIGINT,
    CONSTRAINT "fk_Annotations_param_id__id" FOREIGN KEY ("param_id") REFERENCES "MethodParameters" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Annotations_class_id__id" FOREIGN KEY ("class_id") REFERENCES "Classes" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Annotations_field_id__id" FOREIGN KEY ("field_id") REFERENCES "Fields" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Annotations_annotation_name__id" FOREIGN KEY ("annotation_name") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Annotations_method_id__id" FOREIGN KEY ("method_id") REFERENCES "Methods" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_Annotations_parent_annotation__id" FOREIGN KEY ("parent_annotation") REFERENCES "Annotations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS "AnnotationValues"(
    "id"                BIGINT PRIMARY KEY,
    "annotation_id"     BIGINT       NOT NULL,
    "name"              VARCHAR(256) NOT NULL,
    "ref_annotation_id" BIGINT,
    "kind"              INT,
    "value"             TEXT,
    "class_symbol"      BIGINT,
    "enum_value"        BIGINT,
    CONSTRAINT "fk_AnnotationValues_annotation_id__id" FOREIGN KEY ("annotation_id") REFERENCES "Annotations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_AnnotationValues_enum_value__id" FOREIGN KEY ("enum_value") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_AnnotationValues_ref_annotation_id__id" FOREIGN KEY ("ref_annotation_id") REFERENCES "Annotations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT "fk_AnnotationValues_class_symbol__id" FOREIGN KEY ("class_symbol") REFERENCES "Symbols" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
);

VACUUM;