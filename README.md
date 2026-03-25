# OpenAPI 3.1 Migration Recipe

OpenRewrite-Rezept zur automatisierten Migration von **springdoc-openapi 2.x (OpenAPI 3.0)** auf **OpenAPI 3.1**.

## Hintergrund

OpenAPI 3.1 basiert vollständig auf JSON Schema Draft 2020-12. Die wichtigsten Änderungen gegenüber 3.0:

| OA 3.0 | OA 3.1 |
|--------|--------|
| `nullable: true` (separates Feld) | `type: ["string", "null"]` (Array) |
| `exclusiveMinimum: true` + `minimum: X` | `exclusiveMinimum: X` (numerischer Wert) |
| `springdoc.api-docs.version` fehlt | `springdoc.api-docs.version=openapi_3_1` nötig |

## Enthaltene Rezepte

| Rezept | Was es tut |
|--------|-----------|
| `EnableOpenApi31PropertiesRecipe` | Setzt `springdoc.api-docs.version=openapi_3_1` in `application.properties` |
| `NullableSchemaRecipe` | Migriert `@Schema(nullable = true)` → `@Schema(types = {"T", "null"})` |
| `ExclusiveMinMaxRecipe` | Migriert `@Schema(minimum = "X", exclusiveMinimum = true)` → `@Schema(exclusiveMinimum = "X")` |
| `SpringdocOpenApi31Recipe` | **Composite** — führt alle obigen Rezepte aus |

## Voraussetzungen

- Java 17+
- Maven 3.9+
- springdoc-openapi 2.x im Zielprojekt

## Anwendung

### 1. Rezept bauen und ins lokale Repository installieren

```bash
# Hinweis: Tests benötigen JDK 21 (rewrite-java-17 nutzt JDK-21-kompatible interne APIs)
export JAVA21_HOME=/pfad/zu/jdk-21
mvn install
```

### 2. Rezept im Zielprojekt anwenden

Entweder direkt via Maven-Plugin:

```bash
cd mein-spring-projekt/
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=de.demo:openapi-31-migration-recipe:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=de.demo.openapi.rewrite.SpringdocOpenApi31Recipe
```

Oder als Plugin in der `pom.xml` des Zielprojekts:

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>5.x</version>
    <configuration>
        <activeRecipes>
            <recipe>de.demo.openapi.rewrite.SpringdocOpenApi31Recipe</recipe>
        </activeRecipes>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>de.demo</groupId>
            <artifactId>openapi-31-migration-recipe</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
</plugin>
```

### 3. OpenAPI-Version nach Anwendung prüfen

```bash
# Backend starten
mvn spring-boot:run

# OpenAPI-Version in der exportierten Spec prüfen
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
// Vorher (Legacy-Stil)
@Schema(minimum = "0", exclusiveMinimum = true, maximum = "100", exclusiveMaximum = true)
private int prozent;

// Nachher (OA 3.1 / swagger-core v3 Stil)
@Schema(exclusiveMinimum = "0", exclusiveMaximum = "100")
private int prozent;
```

### application.properties

```properties
# Vorher (fehlt oder falsch)
springdoc.api-docs.version=openapi_3_0

# Nachher
springdoc.api-docs.version=openapi_3_1
```

## Projektstruktur

```
src/
├── main/java/de/demo/openapi/rewrite/
│   ├── NullableSchemaRecipe.java           # @Schema(nullable=true) Migration
│   ├── ExclusiveMinMaxRecipe.java          # exclusiveMin/Max Migration
│   ├── EnableOpenApi31PropertiesRecipe.java # application.properties
│   └── SpringdocOpenApi31Recipe.java        # Composite (alle Sub-Rezepte)
├── main/resources/META-INF/rewrite/
│   └── springdoc-openapi-31.yml            # YAML-Deklaration
└── test/java/de/demo/openapi/rewrite/
    ├── NullableSchemaRecipeTest.java        # 7 Tests
    ├── ExclusiveMinMaxRecipeTest.java       # 6 Tests
    ├── EnableOpenApi31PropertiesRecipeTest.java # 4 Tests
    └── SpringdocOpenApi31RecipeTest.java    # 3 Integrationstests
```
