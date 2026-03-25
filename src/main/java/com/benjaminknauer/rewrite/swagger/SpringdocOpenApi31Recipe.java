package com.benjaminknauer.rewrite.swagger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenRewrite-Composite-Rezept: Vollständige, konfigurierbare Migration von
 * springdoc-openapi 2.x (OpenAPI 3.0) auf OpenAPI 3.1.
 *
 * <p>Alle vier Sub-Rezepte sind per {@code @Option} einzeln aktivierbar/deaktivierbar.
 * Standard (wenn eine Option {@code null} ist): <strong>aktiviert</strong>.</p>
 *
 * <p>Anwendung via Maven (alle Sub-Rezepte aktiv):</p>
 * <pre>
 * mvn rewrite:run \
 *   -Drewrite.recipeArtifactCoordinates=de.demo:openapi-31-migration-recipe:1.0.0-SNAPSHOT \
 *   -Drewrite.activeRecipes=de.demo.openapi.rewrite.SpringdocOpenApi31Recipe
 * </pre>
 *
 * <p>Konfiguriert in YAML (nur bestimmte Sub-Rezepte):</p>
 * <pre>
 * recipeList:
 *   - de.demo.openapi.rewrite.SpringdocOpenApi31Recipe:
 *       migrateExamples: false
 * </pre>
 */
public class SpringdocOpenApi31Recipe extends Recipe {

    @Option(
        displayName = "OA 3.1 in application.properties aktivieren",
        description = "Setzt 'springdoc.api-docs.version=openapi_3_1' in application.properties.",
        required = false
    )
    @Nullable
    private final Boolean enableOpenApi31Properties;

    @Option(
        displayName = "nullable migrieren",
        description = "Migriert @Schema(nullable=true) zu @Schema(types={\"T\",\"null\"}).",
        required = false
    )
    @Nullable
    private final Boolean migrateNullable;

    @Option(
        displayName = "exclusiveMinimum/exclusiveMaximum migrieren",
        description = "Migriert das Legacy-Pattern minimum+exclusiveMinimum=true zu exclusiveMinimum=\"X\".",
        required = false
    )
    @Nullable
    private final Boolean migrateExclusiveMinMax;

    @Option(
        displayName = "example zu examples migrieren",
        description = "Migriert @Schema(example=\"X\") zu @Schema(examples={\"X\"}).",
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
        return "Migriere springdoc-openapi 2.x auf OpenAPI 3.1";
    }

    @Override
    public String getDescription() {
        return "Vollständige, konfigurierbare Migration von springdoc-openapi 2.x (OpenAPI 3.0) "
            + "auf OpenAPI 3.1. Jedes Sub-Rezept kann einzeln aktiviert oder deaktiviert werden.";
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
