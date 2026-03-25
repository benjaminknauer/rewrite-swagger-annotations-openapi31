# rewrite-swagger-annotations-openapi31

OpenRewrite-Rezepte zur automatisierten Migration von **Swagger-Annotationen** (`io.swagger.v3.oas.annotations`) von **OpenAPI 3.0 auf OpenAPI 3.1**.

## Hintergrund

OpenAPI 3.1 basiert vollständig auf JSON Schema Draft 2020-12. Die wichtigsten Änderungen an den Java-Annotationen:

| OA 3.0 (swagger-annotations) | OA 3.1 (swagger-annotations-jakarta 2.2.x) |
|-------------------------------|---------------------------------------------|
| `@Schema(nullable = true)` | `@Schema(types = {"string", "null"})` |
| `@Schema(minimum = "X", exclusiveMinimum = true)` | `@Schema(exclusiveMinimumValue = X)` |
| `@Schema(example = "X")` | `@Schema(examples = {"X"})` |
| `springdoc.api-docs.version=openapi_3_0` | `springdoc.api-docs.version=openapi_3_1` |

## Enthaltene Rezepte

| Rezept | Was es tut |
|--------|-----------|
| `NullableSchemaRecipe` | `@Schema(nullable = true)` → `@Schema(types = {"T", "null"})` |
| `ExclusiveMinMaxRecipe` | `@Schema(minimum = "X", exclusiveMinimum = true)` → `@Schema(exclusiveMinimumValue = X)` |
| `ExampleMigrationRecipe` | `@Schema(example = "X")` → `@Schema(examples = {"X"})` |
| `EnableOpenApi31PropertiesRecipe` | Setzt `springdoc.api-docs.version=openapi_3_1` in `application.properties` **und** `application.yml` |
| `SpringdocOpenApi31Recipe` | **Composite** — führt alle obigen Rezepte aus (einzeln konfigurierbar) |

## Voraussetzungen

- Java 17+
- Maven 3.9+
- springdoc-openapi 2.x im Zielprojekt

## Anwendung

### 1. Rezept bauen und ins lokale Repository installieren

```bash
# Tests benötigen JDK 21 (rewrite-java-17 nutzt JDK-21-interne APIs)
export JAVA21_HOME=/pfad/zu/jdk-21
mvn install
```

### 2. Rezept im Zielprojekt anwenden

Direkt via Maven-Plugin:

```bash
cd mein-spring-projekt/
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.benjaminknauer:rewrite-swagger-annotations-openapi31:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe
```

Oder als Plugin in der `pom.xml` des Zielprojekts:

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>5.x</version>
    <configuration>
        <activeRecipes>
            <recipe>com.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe</recipe>
        </activeRecipes>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>com.benjaminknauer</groupId>
            <artifactId>rewrite-swagger-annotations-openapi31</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
</plugin>
```

### 3. Konfiguration — einzelne Sub-Rezepte deaktivieren

```xml
<activeRecipes>
    <recipe>com.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe</recipe>
</activeRecipes>
<recipeOptions>
    <!-- Alle Optionen sind optional; Standard: true (aktiviert) -->
    <com.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe>
        <migrateExamples>false</migrateExamples>
    </com.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe>
</recipeOptions>
```

Oder via YAML-Preset `SpringdocOpenApi31MinimalRecipe` (nullable + exclusiveMinMax, ohne example-Migration).

### 4. OpenAPI-Version nach Anwendung prüfen

```bash
mvn spring-boot:run
curl http://localhost:8080/v3/api-docs | jq .openapi
# Erwartung: "3.1.0"
```

## Transformationsbeispiele

### nullable

```java
// Vorher (OA 3.0)
@Schema(nullable = true)
private String name;

@Schema(type = "string", nullable = true)
private String email;

// Nachher (OA 3.1)
@Schema(types = {"string", "null"})
private String name;

@Schema(types = {"string", "null"})
private String email;
```

### exclusiveMinimum / exclusiveMaximum

```java
// Vorher (OA 3.0 Legacy-Stil)
@Schema(minimum = "0", exclusiveMinimum = true, maximum = "100", exclusiveMaximum = true)
private int prozent;

// Nachher (OA 3.1 — int-Attribute)
@Schema(exclusiveMinimumValue = 0, exclusiveMaximumValue = 100)
private int prozent;
```

### example → examples

```java
// Vorher (OA 3.0)
@Schema(description = "Benutzername", example = "max.mustermann")
private String username;

// Nachher (OA 3.1)
@Schema(description = "Benutzername", examples = {"max.mustermann"})
private String username;
```

### application.properties

```properties
# Vorher
springdoc.api-docs.version=openapi_3_0

# Nachher
springdoc.api-docs.version=openapi_3_1
```

### application.yml

```yaml
# Vorher
springdoc:
  api-docs:
    version: openapi_3_0

# Nachher
springdoc:
  api-docs:
    version: openapi_3_1
```

Fehlt der Eintrag komplett, wird er in beiden Formaten automatisch ergänzt.

## Projektstruktur

```
src/
├── main/java/com/benjaminknauer/rewrite/swagger/
│   ├── NullableSchemaRecipe.java              # @Schema(nullable=true) Migration
│   ├── ExclusiveMinMaxRecipe.java             # exclusiveMin/Max → int-Attribute
│   ├── ExampleMigrationRecipe.java            # example → examples-Array
│   ├── EnableOpenApi31PropertiesRecipe.java   # application.properties
│   └── SpringdocOpenApi31Recipe.java          # Composite (alle Sub-Rezepte, konfigurierbar)
├── main/resources/META-INF/rewrite/
│   └── springdoc-openapi-31.yml               # YAML-Deklaration (3 Preset-Rezepte)
└── test/java/com/benjaminknauer/rewrite/swagger/
    ├── NullableSchemaRecipeTest.java           # 7 Tests
    ├── ExclusiveMinMaxRecipeTest.java          # 7 Tests
    ├── ExampleMigrationRecipeTest.java         # 8 Tests
    ├── EnableOpenApi31PropertiesRecipeTest.java # 7 Tests (properties + yaml)
    └── SpringdocOpenApi31RecipeTest.java       # 13 Tests (inkl. @Nested Konfiguration)
                                               # Gesamt: 39 Tests

```
