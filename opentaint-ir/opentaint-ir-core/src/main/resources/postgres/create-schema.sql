CREATE UNLOGGED TABLE IF NOT EXISTS "BytecodeLocations"(
                                                  "id"         BIGINT PRIMARY KEY,
                                                  "path"       VARCHAR(1024) NOT NULL,
                                                  "uniqueId"       VARCHAR(1024) NOT NULL,
                                                  "runtime"    BOOLEAN       NOT NULL DEFAULT false,
                                                  "state"      INT           NOT NULL DEFAULT 0,
                                                  "updated_id" BIGINT,
                                                  CONSTRAINT "fk_BytecodeLocations_updated_id__id" FOREIGN KEY ("updated_id") REFERENCES "BytecodeLocations" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "Symbols"(
                                        "id"   BIGINT PRIMARY KEY,
                                        "name" VARCHAR(256) NOT NULL
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "OuterClasses"(
                                             "id"                  BIGINT PRIMARY KEY,
                                             "outer_class_name_id" BIGINT NOT NULL,
                                             "name"                VARCHAR(256),
                                             "method_name"         TEXT,
                                             "method_desc"         TEXT
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "Classes"(
                                        "id"           BIGINT PRIMARY KEY,
                                        "access"       INT    NOT NULL,
                                        "name"         BIGINT NOT NULL,
                                        "signature"    TEXT,
                                        "bytecode"     BYTEA  NOT NULL,
                                        "location_id"  BIGINT NOT NULL,
                                        "package_id"   BIGINT NOT NULL,
                                        "outer_class"  BIGINT,
                                        "outer_method" BIGINT
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "Methods"(
                                        "id"           BIGINT PRIMARY KEY,
                                        "access"       INT    NOT NULL,
                                        "name"         BIGINT NOT NULL,
                                        "signature"    TEXT,
                                        "desc"         TEXT,
                                        "return_class" BIGINT,
                                        "class_id"     BIGINT NOT NULL
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "ClassHierarchies"(
    "id"           SERIAL PRIMARY KEY,
    "class_id"     BIGINT  NOT NULL,
    "super_id"     BIGINT  NOT NULL,
    "is_class_ref" BOOLEAN NOT NULL
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "ClassInnerClasses"(
    "id"             SERIAL PRIMARY KEY,
    "class_id"       BIGINT NOT NULL,
    "inner_class_id" BIGINT NOT NULL
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "MethodParameters"(
    "id"              BIGINT PRIMARY KEY,
    "access"          INT    NOT NULL,
    "index"           INT    NOT NULL,
    "name"            VARCHAR(256),
    "parameter_class" BIGINT NOT NULL,
    "method_id"       BIGINT NOT NULL
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "Fields"(
    "id"          BIGINT PRIMARY KEY,
    "access"      INT    NOT NULL,
    "name"        BIGINT NOT NULL,
    "signature"   TEXT,
    "field_class" BIGINT NOT NULL,
    "class_id"    BIGINT NOT NULL
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "Annotations"(
    "id"                BIGINT PRIMARY KEY,
    "annotation_name"   BIGINT  NOT NULL,
    "visible"           BOOLEAN NOT NULL,
    "parent_annotation" BIGINT,
    "class_id"          BIGINT,
    "method_id"         BIGINT,
    "field_id"          BIGINT,
    "param_id"          BIGINT
) WITH (autovacuum_enabled=false);

CREATE UNLOGGED TABLE IF NOT EXISTS "AnnotationValues"(
    "id"                BIGINT PRIMARY KEY,
    "annotation_id"     BIGINT       NOT NULL,
    "name"              VARCHAR(256) NOT NULL,
    "ref_annotation_id" BIGINT,
    "kind"              INT,
    "value"             TEXT,
    "class_symbol"      BIGINT,
    "enum_value"        BIGINT
) WITH (autovacuum_enabled=false);
