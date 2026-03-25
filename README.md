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

### Option A: OpenRewrite CLI (empfohlen — keine Änderung am Projekt nötig)

Die [OpenRewrite CLI](https://docs.openrewrite.org/running-recipes/getting-started) führt das Rezept direkt im Zielprojekt aus, ohne die `pom.xml` zu verändern:

```bash
cd mein-spring-projekt/

# Vollständige Migration (alle Sub-Rezepte)
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe
```

Nur bestimmte Sub-Rezepte anwenden:

```bash
# Nur nullable migrieren
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.NullableSchemaRecipe

# Nur example → examples
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.ExampleMigrationRecipe
```

Vorschau ohne Änderungen (dry run):

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:dryRun \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe
```

### Option B: Plugin in der pom.xml des Zielprojekts

Für dauerhafte Konfiguration oder CI-Integration:

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

Dann ausführen mit:

```bash
mvn rewrite:run
```

### Option C: Eigene rewrite.yml (empfohlen für projektspezifische Konfiguration)

Eine `rewrite.yml` im Projektroot erlaubt es, das Rezept mit eigenen Parametern zu konfigurieren — ohne die `pom.xml` zu ändern und ohne lange Kommandozeilenargumente.

**Schritt 1:** `rewrite.yml` im Root des Zielprojekts anlegen:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.mycompany.OpenApi31Migration
displayName: OpenAPI 3.1 Migration (angepasst)
recipeList:
  - io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe:
      enableOpenApi31Properties: true
      migrateNullable: true
      migrateExclusiveMinMax: true
      migrateExamples: false   # example-Migration bewusst deaktiviert
```

**Schritt 2:** Plugin in `pom.xml` einbinden und eigenes Rezept aktivieren:

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

**Schritt 3:** Ausführen:

```bash
mvn rewrite:run
# oder nur Vorschau:
mvn rewrite:dryRun
```

Verfügbare Parameter für `SpringdocOpenApi31Recipe`:

| Parameter | Typ | Standard | Beschreibung |
|-----------|-----|---------|--------------|
| `enableOpenApi31Properties` | Boolean | `true` | `springdoc.api-docs.version` in properties/yml setzen |
| `migrateNullable` | Boolean | `true` | `nullable=true` → `types={"T","null"}` |
| `migrateExclusiveMinMax` | Boolean | `true` | `minimum+exclusiveMinimum=true` → `exclusiveMinimumValue` |
| `migrateExamples` | Boolean | `true` | `example="X"` → `examples={"X"}` |

### Fertige Preset-Rezepte

Alternativ zu eigener Konfiguration gibt es drei vordefinierte Varianten:

| Rezept | Properties | nullable | exclusiveMinMax | examples |
|--------|-----------|---------|-----------------|---------|
| `SpringdocOpenApi31Recipe` | ✅ | ✅ | ✅ | ✅ |
| `SpringdocOpenApi31AnnotationsOnlyRecipe` | ❌ | ✅ | ✅ | ✅ |
| `SpringdocOpenApi31MinimalRecipe` | ✅ | ✅ | ✅ | ❌ |

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
  -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31MinimalRecipe
```

### OpenAPI-Version nach Anwendung prüfen

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
├── main/java/io/github/benjaminknauer/rewrite/swagger/
│   ├── NullableSchemaRecipe.java              # @Schema(nullable=true) Migration
│   ├── ExclusiveMinMaxRecipe.java             # exclusiveMin/Max → int-Attribute
│   ├── ExampleMigrationRecipe.java            # example → examples-Array
│   ├── EnableOpenApi31PropertiesRecipe.java   # application.properties + application.yml
│   └── SpringdocOpenApi31Recipe.java          # Composite (alle Sub-Rezepte, konfigurierbar)
├── main/resources/META-INF/rewrite/
│   └── springdoc-openapi-31.yml               # YAML-Deklaration (3 Preset-Rezepte)
└── test/java/io/github/benjaminknauer/rewrite/swagger/
    ├── NullableSchemaRecipeTest.java           # 7 Tests
    ├── ExclusiveMinMaxRecipeTest.java          # 7 Tests
    ├── ExampleMigrationRecipeTest.java         # 8 Tests
    ├── EnableOpenApi31PropertiesRecipeTest.java # 7 Tests (properties + yaml)
    └── SpringdocOpenApi31RecipeTest.java       # 13 Tests (inkl. @Nested Konfiguration)
                                               # Gesamt: 39 Tests
```

## Lizenz

Apache License 2.0 — siehe [LICENSE](LICENSE)
