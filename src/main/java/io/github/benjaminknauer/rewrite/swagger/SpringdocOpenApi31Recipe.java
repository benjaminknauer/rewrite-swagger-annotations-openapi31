package io.github.benjaminknauer.rewrite.swagger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OpenRewrite-Composite-Rezept: Vollständige, konfigurierbare Migration von
 * springdoc-openapi 2.x (OpenAPI 3.0) auf OpenAPI 3.1.
 *
 * <p>Alle vier Sub-Rezepte sind per {@code @Option} einzeln aktivierbar/deaktivierbar.
 * Standard (wenn eine Option {@code null} ist): <strong>aktiviert</strong>.</p>
 *
 * <p>Anwendung via Maven (alle Sub-Rezepte aktiv):</p>
 * <pre>
 * mvn org.openrewrite.maven:rewrite-maven-plugin:run \
 *   -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
 *   -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe
 * </pre>
 *
 * <p>Konfiguriert via {@code rewrite.yml} im Projektroot (nur bestimmte Sub-Rezepte):</p>
 * <pre>
 * ---
 * type: specs.openrewrite.org/v1beta/recipe
 * name: com.mycompany.MyMigration
 * recipeList:
 *   - io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe:
 *       migrateExamples: false
 *       enableOpenApi31Properties: false
 * </pre>
 */
public class SpringdocOpenApi31Recipe extends Recipe {

    @Option(
        displayName = "application.properties / application.yml aktualisieren",
        description = "Setzt 'springdoc.api-docs.version=openapi_3_1' in application.properties "
            + "bzw. application.yml. Fügt den Eintrag hinzu, falls er fehlt. "
            + "Auf 'false' setzen, wenn die Konfigurationsdatei nicht verändert werden soll.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean enableOpenApi31Properties;

    @Option(
        displayName = "@Schema(nullable=true) migrieren",
        description = "Ersetzt 'nullable = true' durch 'types = {\"string\", \"null\"}' (bzw. den "
            + "deklarierten Typ). In OpenAPI 3.1 / JSON Schema 2020-12 gibt es kein 'nullable' mehr. "
            + "Auf 'false' setzen, wenn diese Migration übersprungen werden soll.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean migrateNullable;

    @Option(
        displayName = "exclusiveMinimum / exclusiveMaximum migrieren",
        description = "Ersetzt das OpenAPI-3.0-Pattern '@Schema(minimum = \"X\", exclusiveMinimum = true)' "
            + "durch '@Schema(exclusiveMinimumValue = X)' (int). In OpenAPI 3.1 sind exclusiveMinimum und "
            + "exclusiveMaximum numerische Werte, keine Boolean-Flags mehr. "
            + "Nicht-ganzzahlige Werte werden übersprungen. "
            + "Auf 'false' setzen, wenn diese Migration übersprungen werden soll.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean migrateExclusiveMinMax;

    @Option(
        displayName = "@Schema(example=...) migrieren",
        description = "Ersetzt das singuläre 'example = \"X\"' durch 'examples = {\"X\"}'. "
            + "In JSON Schema Draft 2020-12 (Basis von OpenAPI 3.1) ist 'examples' ein Array. "
            + "Bereits vorhandene 'examples'-Attribute werden nicht verändert (idempotent). "
            + "Auf 'false' setzen, wenn diese Migration übersprungen werden soll.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean migrateExamples;

    /** Standardkonstruktor: alle Sub-Rezepte aktiv. */
    public SpringdocOpenApi31Recipe() {
        this(null, null, null, null);
    }

    @JsonCreator
    public SpringdocOpenApi31Recipe(
        @JsonProperty("enableOpenApi31Properties") @Nullable Boolean enableOpenApi31Properties,
        @JsonProperty("migrateNullable") @Nullable Boolean migrateNullable,
        @JsonProperty("migrateExclusiveMinMax") @Nullable Boolean migrateExclusiveMinMax,
        @JsonProperty("migrateExamples") @Nullable Boolean migrateExamples
    ) {
        this.enableOpenApi31Properties = enableOpenApi31Properties;
        this.migrateNullable = migrateNullable;
        this.migrateExclusiveMinMax = migrateExclusiveMinMax;
        this.migrateExamples = migrateExamples;
    }

    @Override
    public String getDisplayName() {
        return "Migriere springdoc-openapi 2.x Swagger-Annotationen auf OpenAPI 3.1";
    }

    @Override
    public String getDescription() {
        return "Vollständige, konfigurierbare Migration von springdoc-openapi 2.x (OpenAPI 3.0) "
            + "auf OpenAPI 3.1: nullable, exclusiveMinimum/Maximum, example und "
            + "springdoc.api-docs.version werden automatisch migriert. "
            + "Jedes Sub-Rezept kann einzeln aktiviert oder deaktiviert werden.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("openapi", "swagger", "springdoc", "migration");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(15);
    }

    @Override
    public List<Recipe> getRecipeList() {
        List<Recipe> recipes = new ArrayList<>();
        if (isEnabled(enableOpenApi31Properties)) recipes.add(new EnableOpenApi31PropertiesRecipe());
        if (isEnabled(migrateNullable))           recipes.add(new NullableSchemaRecipe());
        if (isEnabled(migrateExclusiveMinMax))    recipes.add(new ExclusiveMinMaxRecipe());
        if (isEnabled(migrateExamples))           recipes.add(new ExampleMigrationRecipe());
        return recipes;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return TreeVisitor.noop();
    }

    /** {@code null} oder {@code true} → aktiviert; {@code false} → deaktiviert. */
    private boolean isEnabled(@Nullable Boolean option) {
        return option == null || option;
    }
}
