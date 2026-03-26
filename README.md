# rewrite-swagger-annotations-openapi31

[![Maven Central](https://img.shields.io/maven-central/v/io.github.benjaminknauer/rewrite-swagger-annotations-openapi31.svg)](https://central.sonatype.com/artifact/io.github.benjaminknauer/rewrite-swagger-annotations-openapi31)
[![Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

OpenRewrite recipe for automated migration of **Swagger annotations** (`io.swagger.v3.oas.annotations`) from **OpenAPI 3.0 to OpenAPI 3.1** in Spring Boot projects using springdoc-openapi 2.x.

## What It Migrates

OpenAPI 3.1 is fully aligned with JSON Schema Draft 2020-12. The recipe handles five changes:

| OA 3.0 | OA 3.1 |
|--------|--------|
| `@Schema(nullable = true)` | `@Nullable` (JSpecify, default) **or** `@Schema(types = {"T", "null"})` |
| `@Schema(type = "X")` | `@Schema(types = {"X"})` |
| `@Schema(minimum = "X", exclusiveMinimum = true)` | `@Schema(exclusiveMinimumValue = X)` |
| `@Schema(example = "X")` | `@Schema(examples = {"X"})` |
| `springdoc.api-docs.version=openapi_3_0` | `springdoc.api-docs.version=openapi_3_1` |

All transformations are idempotent — already-migrated code is left untouched.

## Usage

### Option A: OpenRewrite CLI (recommended — no changes to the project required)

```bash
cd my-spring-project/

mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:VERSION \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe
```

Replace `VERSION` with the current version from the badge above. Preview without making changes:

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:dryRun \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:VERSION \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe
```

### Option B: Plugin in pom.xml

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>6.35.0</version>
    <configuration>
        <activeRecipes>
            <recipe>io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe</recipe>
        </activeRecipes>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>io.github.benjaminknauer</groupId>
            <artifactId>rewrite-swagger-annotations-openapi31</artifactId>
            <version>VERSION</version>
        </dependency>
    </dependencies>
</plugin>
```

Then run:

```bash
mvn rewrite:run
```

### Option C: rewrite.yml (project-specific configuration)

Create `rewrite.yml` in the project root to configure individual options without modifying `pom.xml`:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.mycompany.OpenApi31Migration
recipeList:
  - io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe:
      migrateExamples: false
      useNullableAnnotation: false
      migrateSingleType: false
```

Then configure the plugin as in Option B and activate `com.mycompany.OpenApi31Migration`.

### Options

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enableOpenApi31Properties` | Boolean | `true` | Set `springdoc.api-docs.version=openapi_3_1` in properties/yml |
| `migrateNullable` | Boolean | `true` | Migrate `nullable=true`; strategy controlled by `useNullableAnnotation` |
| `useNullableAnnotation` | Boolean | `true` | `true`: introduce a `@Nullable` annotation for fields without explicit `type=`. `false`: always use `@Schema(types={"T","null"})` (no new dependency) |
| `nullableAnnotation` | String | `org.jspecify.annotations.Nullable` | Fully qualified name of the `@Nullable` annotation to introduce. Only applies when `useNullableAnnotation` is `true`. Common alternatives: `org.springframework.lang.Nullable`, `jakarta.annotation.Nullable`, `org.jetbrains.annotations.Nullable` |
| `addJSpecifyDependency` | Boolean | `true` | When `useNullableAnnotation` is `true` and the default JSpecify annotation is used: add `org.jspecify:jspecify` to `pom.xml`. Set to `false` when jspecify already arrives as a transitive dependency |
| `migrateExclusiveMinMax` | Boolean | `true` | `minimum+exclusiveMinimum=true` → `exclusiveMinimumValue` |
| `migrateExamples` | Boolean | `true` | `example="X"` → `examples={"X"}` |
| `migrateSingleType` | Boolean | `true` | `type="X"` → `types={"X"}` (runs after nullable migration) |

## Transformation Examples

### nullable

Default strategy (`useNullableAnnotation: true`):

```java
// Before (OA 3.0)
@Schema(nullable = true)
private String name;                           // no explicit type → @Nullable

@Schema(type = "string", nullable = true)
private String email;                          // explicit type → types array

// After (OA 3.1)
@Nullable
private String name;

@Schema(types = {"string", "null"})
private String email;
```

Types-array strategy (`useNullableAnnotation: false`):

```java
// After — types array for all fields (no new dependency)
@Schema(types = {"string", "null"})
private String name;

@Schema(types = {"string", "null"})
private String email;
```

### type → types array

```java
// Before (OA 3.0)
@Schema(type = "string", description = "ISO 4217 currency code")
private String currency;

// After (OA 3.1)
@Schema(types = {"string"}, description = "ISO 4217 currency code")
private String currency;
```

Annotations with `nullable = true` are not affected by this option — they are handled by the nullable migration above, which produces `types = {"T", "null"}`.

### exclusiveMinimum / exclusiveMaximum

```java
// Before (OA 3.0)
@Schema(minimum = "0", exclusiveMinimum = true, maximum = "100", exclusiveMaximum = true)
private int percent;

// After (OA 3.1)
@Schema(exclusiveMinimumValue = 0, exclusiveMaximumValue = 100)
private int percent;
```

### example → examples

```java
// Before
@Schema(description = "Username", example = "john.doe")
private String username;

// After
@Schema(description = "Username", examples = {"john.doe"})
private String username;
```

### application.properties / application.yml

```properties
# Before → After
springdoc.api-docs.version=openapi_3_0 → springdoc.api-docs.version=openapi_3_1
```

If the entry is missing, it is automatically added.

## Nullable Strategy

The recipe applies the best strategy per field:

- **`@Schema` with explicit `type`**: always converts to `@Schema(types = {"T", "null"})`.
- **`@Schema` without `type`** (default `useNullableAnnotation=true`): replaces with a `@Nullable` annotation (default: `org.jspecify.annotations.Nullable`). springdoc-openapi infers the correct types array at runtime.
- The `org.jspecify:jspecify` dependency is added to `pom.xml` automatically when the default JSpecify annotation is introduced (idempotent). Set `addJSpecifyDependency: false` to skip this when jspecify already arrives as a transitive dependency.
- The annotation can be changed via `nullableAnnotation` (e.g. `org.springframework.lang.Nullable`) — useful when projects already have a `@Nullable` annotation on the classpath.

Set `useNullableAnnotation: false` to always use the types-array form — no new dependency is added.

### Why JSpecify Is the Default

The types-array strategy must infer the JSON type from the Java field type at transformation time. This is unreliable for Jackson-configuration-sensitive types like `java.time.*`: with the Spring Boot default (`write-dates-as-timestamps=false`) they serialize as strings, but the raw Java type gives no clue about this. `java.time` types are intentionally excluded from the mapping.

The JSpecify strategy avoids this entirely — springdoc infers the correct type at runtime, after Jackson serialization. swagger-core recognises any annotation named `@Nullable` (regardless of package) and emits `["T", "null"]` automatically — implemented in [swagger-core PR #5018](https://github.com/swagger-api/swagger-core/pull/5018).

### Transformation Rules

```java
// @Schema with explicit type → types array
@Schema(type = "string", nullable = true) private String email;
// → @Schema(types = {"string", "null"})

// @Schema with no other attrs → entire @Schema removed
@Schema(nullable = true) private String name;
// → @Nullable

// @Schema with other attrs → nullable stripped, rest preserved
@Schema(nullable = true, description = "ISO country code") private String countryCode;
// → @Nullable + @Schema(description = "ISO country code")
```

## Limitations

1. **`example` → `examples` is enabled by default.** The change affects the generated JSON output (`"example": "foo"` → `"examples": ["foo"]`), which can break tools or client generators that look for the singular field. Set `migrateExamples: false` if any consumers of your API spec cannot yet handle the `examples` array form.

2. **`exclusiveMinimumValue` is `int`, not `double`.** Decimal boundary values (e.g. `minimum = "1.5"`) are skipped silently and require manual migration.

3. **Only `@Schema` is handled.** `@Parameter.example` maps to the OA parameter-level `example` field, which is still valid in OA 3.1 and is not touched.

4. **Standalone YAML/JSON OpenAPI specs are not migrated.** Spec-first workflows with hand-written `openapi.yaml` are out of scope.

5. **Fields without any nullability annotation are not touched.** A field like `private String name;` with no `@Schema(nullable=true)` remains unchanged.

## Prerequisites

- Java 17+
- Maven 3.9+
- springdoc-openapi 2.x in the target project

## License

Apache License 2.0 — see [LICENSE](LICENSE)
