# rewrite-swagger-annotations-openapi31

OpenRewrite recipes for automated migration of **Swagger annotations** (`io.swagger.v3.oas.annotations`) from **OpenAPI 3.0 to OpenAPI 3.1**.

## Background

OpenAPI 3.1 is fully based on JSON Schema Draft 2020-12. The most important changes to Java annotations:

| OA 3.0 (swagger-annotations) | OA 3.1 (swagger-annotations-jakarta 2.2.x) |
|-------------------------------|---------------------------------------------|
| `@Schema(nullable = true)` | `@Nullable` (JSpecify, default) **or** `@Schema(types = {"string", "null"})` |
| `@Schema(minimum = "X", exclusiveMinimum = true)` | `@Schema(exclusiveMinimumValue = X)` |
| `@Schema(example = "X")` | `@Schema(examples = {"X"})` |
| `springdoc.api-docs.version=openapi_3_0` | `springdoc.api-docs.version=openapi_3_1` |

## Why These Recipes Are Useful

In large Spring Boot codebases with hundreds of `@Schema` annotations, migrating manually is tedious and error-prone. These recipes automate the three changes that break spec conformance when running springdoc-openapi 2.x in OpenAPI 3.1 mode:

- **`nullable`** is not part of JSON Schema 2020-12. When springdoc runs in OA 3.1 mode it silently drops `nullable = true` from the generated spec — the field then appears as non-nullable (see springdoc issue #2840 and swagger-core issues #4555, #5001). Using `types = {"T", "null"}` is the correct OA 3.1 form.
- **`exclusiveMinimum`/`exclusiveMaximum` as booleans** are an OpenAPI 3.0-specific extension not valid in OA 3.1; the correct form is the numeric `exclusiveMinimumValue`/`exclusiveMaximumValue`.
- **The `springdoc.api-docs.version` property** must be explicitly set to `openapi_3_1`; otherwise the library keeps emitting OA 3.0.

Each recipe is idempotent (already-migrated code is left untouched) and preserves all other annotation attributes.

## Limitations

1. **`example` → `examples`: enabled by default.** The singular `example` field is deprecated in OA 3.1 in favour of the JSON Schema `examples` keyword. The migration changes the generated JSON output (`"example": "foo"` → `"examples": ["foo"]`), which can break tools, client generators, or documentation renderers that look for the singular field. Set `migrateExamples: false` (or use `SpringdocOpenApi31MinimalRecipe`) if any consumers of your API spec cannot yet handle the `examples` array form.

2. **Static type inference in `NullableSchemaRecipe` (types-array strategy) is inherently limited — which is why JSpecify is the default.**

   When `@Schema(nullable = true)` carries no explicit `type` attribute, `NullableSchemaRecipe` must infer the JSON type from the Java field type at transformation time. The mapping covers common cases: `Boolean` → `"boolean"`, `Integer`/`Long` → `"integer"`, `Double`/`Float` → `"number"`, `List`/arrays → `"array"`, enums → `"string"`, unknown classes → `"object"`, with a fallback to `"string"` when the type cannot be resolved.

   **The core problem: the inferred type reflects the Java type, not the actual serialized JSON type**, which depends on your Jackson configuration. The most prominent example: `java.time` types (`LocalDate`, `LocalDateTime`, `Instant`, etc.) are intentionally excluded from the mapping — with Jackson's default (`WRITE_DATES_AS_TIMESTAMPS=true`) they serialize as arrays or numbers, but with `WRITE_DATES_AS_TIMESTAMPS=false` (the Spring Boot default via `spring.jackson.serialization.write-dates-as-timestamps=false`) they become strings. There is no safe universal mapping.

   **The JSpecify strategy (default, `useJSpecifyNullable: true`) avoids this entirely.** For fields without an explicit `type`, it adds `@org.jspecify.annotations.Nullable` to the field and removes `nullable = true` from `@Schema`. springdoc-openapi then infers the correct types array at runtime — after Jackson serialization, with full knowledge of your actual Jackson configuration. This works because swagger-core (the library underlying springdoc) recognises any annotation named `@Nullable` — regardless of package — and automatically emits `["T", "null"]` in the generated spec. This was implemented in [swagger-core PR #5018](https://github.com/swagger-api/swagger-core/pull/5018). Fields with an explicit `type` attribute are always migrated to the types-array form in both strategies, since the type is already known.

   Use `useJSpecifyNullable: false` (or a `TypesArray` preset) only if you explicitly want fully self-contained `@Schema` annotations without a runtime dependency on `org.jspecify:jspecify`. Always review the diff when using the types-array strategy and verify that the inferred type matches your actual JSON output.

3. **`exclusiveMinimumValue` is `int`, not `double`.** Any boundary value with a decimal (e.g. `minimum = "1.5"`) is skipped without a warning. If your API uses decimal boundaries, these will remain in the old OA 3.0 form and require manual migration.

4. **Only `@Schema` is handled.** `@Parameter` also has an `example` attribute, but it maps to the OpenAPI parameter-level `example` field, which is still valid in OA 3.1 and does not need migration. Annotations like `@RequestBody` and `@ApiResponse` do not carry nullable or example attributes directly.

5. **Standalone YAML/JSON OpenAPI specs are not touched.** If you maintain a spec-first workflow with hand-written `openapi.yaml` files, these recipes do not apply.

6. **Fields without any nullability annotation are not touched.** A field like `private String name;` with no `@Schema`, `@Nullable`, or `@NotNull` is treated as non-nullable by springdoc in both OA 3.0 and OA 3.1. This is unchanged by the migration.

## Included Recipes

| Recipe | What it does |
|--------|-------------|
| `SchemaToJSpecifyNullableRecipe` | `@Schema(nullable = true)` → `@org.jspecify.annotations.Nullable` — **recommended** (see [nullable strategy](#nullable-migration-strategy)) |
| `NullableSchemaRecipe` | `@Schema(nullable = true)` → `@Schema(types = {"T", "null"})` — alternative, no new dependency |
| `ExclusiveMinMaxRecipe` | `@Schema(minimum = "X", exclusiveMinimum = true)` → `@Schema(exclusiveMinimumValue = X)` |
| `ExampleMigrationRecipe` | `@Schema(example = "X")` → `@Schema(examples = {"X"})` |
| `EnableOpenApi31PropertiesRecipe` | Sets `springdoc.api-docs.version=openapi_3_1` in `application.properties` **and** `application.yml` |
| `SpringdocOpenApi31Recipe` | **Composite** — runs all of the above (each individually configurable) |

## Recipe Overview

### JSpecify nullable (default — recommended)

| Recipe | Properties | nullable | exclusiveMinMax | examples |
|--------|-----------|---------|-----------------|---------|
| `SpringdocOpenApi31Recipe` | ✅ | `@Nullable` | ✅ | ✅ |
| `SpringdocOpenApi31AnnotationsOnlyRecipe` | ❌ | `@Nullable` | ✅ | ✅ |
| `SpringdocOpenApi31MinimalRecipe` | ✅ | `@Nullable` | ✅ | ❌ |

### Types-array nullable (opt-out — no new dependency)

| Recipe | Properties | nullable | exclusiveMinMax | examples |
|--------|-----------|---------|-----------------|---------|
| `SpringdocOpenApi31TypesArrayRecipe` | ✅ | `types={…}` | ✅ | ✅ |
| `SpringdocOpenApi31TypesArrayAnnotationsOnlyRecipe` | ❌ | `types={…}` | ✅ | ✅ |
| `SpringdocOpenApi31TypesArrayMinimalRecipe` | ✅ | `types={…}` | ✅ | ❌ |

**Recommendation:** Use `SpringdocOpenApi31Recipe` (JSpecify, all migrations active) and review the diff.
Use a `TypesArray` variant if you prefer fully self-contained annotations without a new runtime dependency.
Use a `Minimal` variant if any API consumers cannot yet handle the `examples` array form.

## Prerequisites

- Java 17+
- Maven 3.9+
- springdoc-openapi 2.x in the target project


## Usage

### Option A: OpenRewrite CLI (recommended — no changes to the project required)

The [OpenRewrite CLI](https://docs.openrewrite.org/running-recipes/getting-started) runs the recipe directly in the target project without modifying its `pom.xml`:

```bash
cd my-spring-project/

# Full migration (all sub-recipes)
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe
```

Apply only specific sub-recipes:

```bash
# Migrate nullable only
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.NullableSchemaRecipe

# Migrate example → examples only
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.ExampleMigrationRecipe
```

Preview without making changes (dry run):

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:dryRun \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe
```

### Option B: Plugin in the target project's pom.xml

For permanent configuration or CI integration:

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>5.43.0</version>
    <configuration>
        <activeRecipes>
            <recipe>io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe</recipe>
        </activeRecipes>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>io.github.benjaminknauer</groupId>
            <artifactId>rewrite-swagger-annotations-openapi31</artifactId>
            <version>0.1.0</version>
        </dependency>
    </dependencies>
</plugin>
```

Then run with:

```bash
mvn rewrite:run
```

### Option C: Custom rewrite.yml (recommended for project-specific configuration)

A `rewrite.yml` in the project root allows configuring the recipe with custom parameters — without modifying `pom.xml` and without long command-line arguments.

**Step 1:** Create `rewrite.yml` in the root of the target project:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.mycompany.OpenApi31Migration
displayName: OpenAPI 3.1 Migration (customised)
recipeList:
  - io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe:
      enableOpenApi31Properties: true
      migrateNullable: true
      migrateExclusiveMinMax: true
      migrateExamples: true
```

**Step 2:** Include the plugin in `pom.xml` and activate the custom recipe:

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>5.43.0</version>
    <configuration>
        <activeRecipes>
            <recipe>com.mycompany.OpenApi31Migration</recipe>
        </activeRecipes>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>io.github.benjaminknauer</groupId>
            <artifactId>rewrite-swagger-annotations-openapi31</artifactId>
            <version>0.1.0</version>
        </dependency>
    </dependencies>
</plugin>
```

**Step 3:** Run:

```bash
mvn rewrite:run
# or preview only:
mvn rewrite:dryRun
```

Available parameters for `SpringdocOpenApi31Recipe`:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enableOpenApi31Properties` | Boolean | `true` | Set `springdoc.api-docs.version` in properties/yml |
| `migrateNullable` | Boolean | `true` | Migrate `nullable=true`; strategy controlled by `useJSpecifyNullable` |
| `useJSpecifyNullable` | Boolean | `true` | `true`: `@Nullable` for fields without explicit `type=`, `@Schema(types={…})` for fields with `type=`. `false`: always `@Schema(types={"T","null"})` (no new dependency) |
| `migrateExclusiveMinMax` | Boolean | `true` | `minimum+exclusiveMinimum=true` → `exclusiveMinimumValue` |
| `migrateExamples` | Boolean | `true` | `example="X"` → `examples={"X"}` — `example` is deprecated in OA 3.1; set to `false` if consumers look for the singular field (see limitations) |

### Preset Recipes

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31MinimalRecipe
```

### Verify the OpenAPI version after applying

```bash
mvn spring-boot:run
curl http://localhost:8080/v3/api-docs | jq .openapi
# Expected: "3.1.0"
```

## Transformation Examples

### nullable

```java
// Before (OA 3.0)
@Schema(nullable = true)
private String name;

@Schema(type = "string", nullable = true)
private String email;

// After (OA 3.1)
@Schema(types = {"string", "null"})
private String name;

@Schema(types = {"string", "null"})
private String email;
```

### exclusiveMinimum / exclusiveMaximum

```java
// Before (OA 3.0 legacy style)
@Schema(minimum = "0", exclusiveMinimum = true, maximum = "100", exclusiveMaximum = true)
private int percent;

// After (OA 3.1 — int attributes)
@Schema(exclusiveMinimumValue = 0, exclusiveMaximumValue = 100)
private int percent;
```

### example → examples

```java
// Before
@Schema(description = "Username", example = "john.doe")
private String username;

// After (migrateExamples: true, the default)
@Schema(description = "Username", examples = {"john.doe"})
private String username;
```

> Note: `example` is deprecated in OA 3.1 in favour of `examples`. Set `migrateExamples: false` (or use `SpringdocOpenApi31MinimalRecipe`) if any consumers of your spec look for the singular field.

### application.properties

```properties
# Before
springdoc.api-docs.version=openapi_3_0

# After
springdoc.api-docs.version=openapi_3_1
```

### application.yml

```yaml
# Before
springdoc:
  api-docs:
    version: openapi_3_0

# After
springdoc:
  api-docs:
    version: openapi_3_1
```

If the entry is missing entirely, it is automatically added in both formats.

## Nullable migration strategy

The composite recipe applies the best strategy per field:

- **If `@Schema` carries an explicit `type`**: convert to the OA 3.1 types-array form — `@Schema(types = {"T", "null"})`.
- **If `@Schema` has no explicit `type`** (default strategy, `useJSpecifyNullable=true`): replace with `@org.jspecify.annotations.Nullable` — springdoc-openapi infers the correct type array at runtime automatically.
- The `org.jspecify:jspecify` dependency is automatically added to the target project's `pom.xml` when `@Nullable` is introduced (via OpenRewrite's `AddDependency`, idempotent).

Set `useJSpecifyNullable: false` to always use the types-array form — no new dependency is added.

### Why the two-path approach

The `useJSpecifyNullable=false` (types-array) strategy must infer the JSON type from the Java field type at transformation time. This works for common types, but requires mapping every possible Java type — including Jackson-configuration-sensitive types like `java.time.*`.

The default (`useJSpecifyNullable=true`, JSpecify) avoids this by delegating type inference to springdoc at runtime. If an explicit `type` is present, it is used directly. This is more conservative and less likely to produce incorrect type strings.

### Why this works

swagger-core (the library underlying springdoc-openapi) recognises any annotation named `@Nullable`
— regardless of package — and automatically emits `["T", "null"]` in the generated OpenAPI spec.
This was implemented in [swagger-core PR #5018](https://github.com/swagger-api/swagger-core/pull/5018),
which closed [issue #5001](https://github.com/swagger-api/swagger-core/issues/5001).
The detection uses a simple name match (`"Nullable"`), so JSpecify, Jakarta, Spring, and JetBrains
`@Nullable` annotations all work equally.

### Transformation rules

```java
// Before
@Schema(type = "string", nullable = true)
private String email;                          // → @Schema(types = {"string", "null"})

@Schema(type = "integer", nullable = true, description = "Port")
private Integer port;                          // → @Schema(description = "Port", types = {"integer", "null"})

@Schema(nullable = true)
private String name;                           // → @Nullable (entire @Schema removed)

@Schema(nullable = true, description = "ISO country code")
private String countryCode;                    // → @Nullable + @Schema(description = "ISO country code")
```

```java
// After
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(types = {"string", "null"})
private String email;

@Schema(description = "Port", types = {"integer", "null"})
private Integer port;

@Nullable
private String name;

@Nullable
@Schema(description = "ISO country code")
private String countryCode;
```

**Additional rules:**
- If `@Schema` only carries `nullable = true` and no `type`: the entire `@Schema` is removed.
- If `@Schema` has additional attributes but no `type`: only `nullable` is stripped, the rest is preserved.
- If `@Nullable` is already present alongside a no-`type` `@Schema`: the field is left untouched (idempotent).
- The `org.jspecify:jspecify` dependency is added to `pom.xml` only when `@Nullable` is actually introduced (uses `onlyIfUsing` guard).

### When to use which strategy

| | `useJSpecifyNullable: true` (default) | `useJSpecifyNullable: false` |
|---|---|---|
| Fields with explicit `type=` | `@Schema(types={"X","null"})` | `@Schema(types={"X","null"})` — same |
| Fields without `type=` | `@Nullable`, springdoc infers at runtime | Infers type from Java field, writes `@Schema(types={"T","null"})` |
| `@Schema` annotation kept | Only when other attrs exist (or explicit type) | Always |
| IDE / static analysis | `@Nullable` understood by IDEs and tools | No benefit |
| Jackson config risk | No (runtime inference) | Yes, for unknown types |
| Adds pom.xml dependency | Yes, `org.jspecify:jspecify` (auto, idempotent) | No |

**Recommendation:** Use the default (`useJSpecifyNullable: true`) for cleaner output and IDE null-safety.
Use `useJSpecifyNullable: false` (or a `TypesArray` preset) if you prefer fully self-contained `@Schema`
annotations without a new runtime dependency.

> **Note:** `useJSpecifyNullable=true` and `useJSpecifyNullable=false` are mutually exclusive strategies
> within a single run. The standalone `SchemaToJSpecifyNullableRecipe` and `NullableSchemaRecipe`
> can still be used independently as building blocks in custom `rewrite.yml` compositions.

## Project Structure

```
src/
├── main/java/io/github/benjaminknauer/rewrite/swagger/
│   ├── NullableSchemaRecipe.java                  # @Schema(nullable=true) → types={"T","null"} (building block)
│   ├── SchemaToJSpecifyNullableRecipe.java        # @Schema(nullable=true) → @Nullable (building block)
│   ├── ExclusiveMinMaxRecipe.java                 # exclusiveMin/Max → int attributes
│   ├── ExampleMigrationRecipe.java                # example → examples array
│   ├── EnableOpenApi31PropertiesRecipe.java       # application.properties + application.yml
│   └── SpringdocOpenApi31Recipe.java              # Composite (all sub-recipes, configurable via @Option)
├── main/resources/META-INF/rewrite/
│   └── springdoc-openapi-31.yml                   # YAML declaration (6 preset recipes)
└── test/java/io/github/benjaminknauer/rewrite/swagger/
    ├── NullableSchemaRecipeTest.java               # 10 tests
    ├── SchemaToJSpecifyNullableRecipeTest.java     # 8 tests
    ├── ExclusiveMinMaxRecipeTest.java              # 7 tests
    ├── ExampleMigrationRecipeTest.java             # 8 tests
    ├── EnableOpenApi31PropertiesRecipeTest.java    # 7 tests (properties + yaml)
    └── SpringdocOpenApi31RecipeTest.java           # 13 tests (incl. @Nested configuration)
                                                   # Total: 53 tests
```

## License

Apache License 2.0 — see [LICENSE](LICENSE)
