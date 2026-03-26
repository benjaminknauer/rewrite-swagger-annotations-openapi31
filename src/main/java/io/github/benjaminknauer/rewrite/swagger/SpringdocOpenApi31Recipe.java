package io.github.benjaminknauer.rewrite.swagger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeList;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * OpenRewrite composite recipe: complete, configurable migration of
 * springdoc-openapi 2.x (OpenAPI 3.0) to OpenAPI 3.1.
 *
 * <p>All sub-recipes can be individually enabled or disabled via {@code @Option}.
 * Default (when an option is {@code null}): <strong>enabled</strong>.</p>
 *
 * <p>Nullable strategy: {@code useNullableAnnotation=true} (the default) uses
 * {@link SchemaToJSpecifyNullableRecipe}; {@code useNullableAnnotation=false} uses
 * {@link NullableSchemaRecipe} (types-array in the annotation).</p>
 *
 * <p>Usage via Maven (all sub-recipes active, JSpecify nullable):</p>
 * <pre>
 * mvn org.openrewrite.maven:rewrite-maven-plugin:run \
 *   -Drewrite.recipeArtifactCoordinates=io.github.benjaminknauer:rewrite-swagger-annotations-openapi31:0.1.0 \
 *   -Drewrite.activeRecipes=io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe
 * </pre>
 *
 * <p>Configured via {@code rewrite.yml} in the project root (selective sub-recipes):</p>
 * <pre>
 * ---
 * type: specs.openrewrite.org/v1beta/recipe
 * name: com.mycompany.MyMigration
 * recipeList:
 *   - io.github.benjaminknauer.rewrite.swagger.SpringdocOpenApi31Recipe:
 *       migrateExamples: false
 *       useNullableAnnotation: false
 * </pre>
 */
public class SpringdocOpenApi31Recipe extends Recipe {

    @Option(
        displayName = "Update application.properties / application.yml",
        description = "Sets 'springdoc.api-docs.version=openapi_3_1' in application.properties "
            + "or application.yml. Adds the entry if it is missing. "
            + "Set to 'false' to leave configuration files untouched.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean enableOpenApi31Properties;

    @Option(
        displayName = "Migrate @Schema(nullable=true)",
        description = "Removes 'nullable = true' and applies the nullable strategy selected by "
            + "'useNullableAnnotation'. OpenAPI 3.1 / JSON Schema 2020-12 no longer has 'nullable'. "
            + "Set to 'false' to skip nullable migration entirely.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean migrateNullable;

    @Option(
        displayName = "Use @Nullable annotation strategy",
        description = "When 'migrateNullable' is true: use a @Nullable annotation "
            + "for fields without an explicit 'type' attribute in @Schema. Fields with an explicit "
            + "'type' always get @Schema(types={\"T\",\"null\"}). "
            + "Set to 'false' to use @Schema(types={\"T\",\"null\"}) for all fields instead "
            + "(no new dependency is added). Default: true.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean useNullableAnnotation;

    @Option(
        displayName = "Nullable annotation",
        description = "Fully qualified name of the @Nullable annotation to introduce. "
            + "Defaults to 'org.jspecify.annotations.Nullable' (JSpecify). "
            + "Other common choices: 'org.springframework.lang.Nullable', "
            + "'jakarta.annotation.Nullable', 'org.jetbrains.annotations.Nullable'. "
            + "Only applies when 'useNullableAnnotation' is true.",
        example = "org.springframework.lang.Nullable",
        required = false
    )
    @Nullable
    private final String nullableAnnotation;

    @Option(
        displayName = "Add org.jspecify:jspecify to pom.xml",
        description = "When 'useNullableAnnotation' is true and the default JSpecify annotation "
            + "is used: add 'org.jspecify:jspecify' as a compile dependency to pom.xml. "
            + "Set to 'false' when jspecify already arrives as a transitive dependency. "
            + "Default: true.",
        example = "false",
        required = false
    )
    @Nullable
    private final Boolean addJSpecifyDependency;

    @Option(
        displayName = "Migrate exclusiveMinimum / exclusiveMaximum",
        description = "Replaces the OpenAPI 3.0 pattern '@Schema(minimum = \"X\", exclusiveMinimum = true)' "
            + "with '@Schema(exclusiveMinimumValue = X)' (int). In OpenAPI 3.1, exclusiveMinimum and "
            + "exclusiveMaximum are numeric values, no longer boolean flags. "
            + "Non-integer values are skipped. "
            + "Set to 'false' to skip this migration.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean migrateExclusiveMinMax;

    @Option(
        displayName = "Migrate @Schema(example=...)",
        description = "Replaces the deprecated singular 'example = \"X\"' with 'examples = {\"X\"}'. "
            + "'example' is deprecated in OpenAPI 3.1 in favour of the JSON Schema 'examples' keyword. "
            + "Enabled by default. Set to 'false' if any consumers of your API spec look for the "
            + "singular 'example' field and cannot yet handle the 'examples' array form "
            + "(see also SpringdocOpenApi31MinimalRecipe).",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean migrateExamples;

    @Option(
        displayName = "Migrate @Schema(type=...) to types array",
        description = "Replaces the singular OpenAPI 3.0 attribute 'type = \"X\"' in @Schema "
            + "with the OpenAPI 3.1 compliant array 'types = {\"X\"}'. "
            + "In OpenAPI 3.1, the JSON Schema 'type' keyword is always an array. "
            + "Annotations with 'nullable = true' are not affected by this option — they are "
            + "handled by 'migrateNullable'. "
            + "Enabled by default.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean migrateSingleType;

    /** All sub-recipes enabled by default; nullable strategy: JSpecify. */
    public SpringdocOpenApi31Recipe() {
        this(null, null, null, null, null, null, null, null);
    }

    @JsonCreator
    public SpringdocOpenApi31Recipe(
        @JsonProperty("enableOpenApi31Properties") @Nullable Boolean enableOpenApi31Properties,
        @JsonProperty("migrateNullable") @Nullable Boolean migrateNullable,
        @JsonProperty("useNullableAnnotation") @Nullable Boolean useNullableAnnotation,
        @JsonProperty("nullableAnnotation") @Nullable String nullableAnnotation,
        @JsonProperty("migrateExclusiveMinMax") @Nullable Boolean migrateExclusiveMinMax,
        @JsonProperty("migrateExamples") @Nullable Boolean migrateExamples,
        @JsonProperty("migrateSingleType") @Nullable Boolean migrateSingleType,
        @JsonProperty("addJSpecifyDependency") @Nullable Boolean addJSpecifyDependency
    ) {
        this.enableOpenApi31Properties = enableOpenApi31Properties;
        this.migrateNullable = migrateNullable;
        this.useNullableAnnotation = useNullableAnnotation;
        this.nullableAnnotation = nullableAnnotation;
        this.migrateExclusiveMinMax = migrateExclusiveMinMax;
        this.migrateExamples = migrateExamples;
        this.migrateSingleType = migrateSingleType;
        this.addJSpecifyDependency = addJSpecifyDependency;
    }

    @Override
    public String getDisplayName() {
        return "Migrate springdoc-openapi 2.x Swagger annotations to OpenAPI 3.1";
    }

    @Override
    public String getDescription() {
        return "Complete, configurable migration of springdoc-openapi 2.x (OpenAPI 3.0) "
            + "to OpenAPI 3.1: nullable, exclusiveMinimum/Maximum, example and "
            + "springdoc.api-docs.version are migrated automatically. "
            + "Each sub-recipe can be individually enabled or disabled.";
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
    public void buildRecipeList(RecipeList recipes) {
        if (isEnabled(enableOpenApi31Properties)) recipes.recipe(new EnableOpenApi31PropertiesRecipe());
        if (isEnabled(migrateNullable)) {
            if (isEnabled(useNullableAnnotation)) recipes.recipe(new SchemaToJSpecifyNullableRecipe(nullableAnnotation, addJSpecifyDependency));
            else                                recipes.recipe(new NullableSchemaRecipe());
        }
        if (isEnabled(migrateExclusiveMinMax)) recipes.recipe(new ExclusiveMinMaxRecipe());
        if (isEnabled(migrateExamples))        recipes.recipe(new ExampleMigrationRecipe());
        // Runs last: converts remaining type="X" (without nullable) to types={"X"}
        if (isEnabled(migrateSingleType))      recipes.recipe(new SchemaTypeToTypesArrayRecipe());
    }

    /** {@code null} or {@code true} → enabled; {@code false} → disabled. */
    private boolean isEnabled(@Nullable Boolean option) {
        return option == null || option;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpringdocOpenApi31Recipe that)) return false;
        return Objects.equals(enableOpenApi31Properties, that.enableOpenApi31Properties)
            && Objects.equals(migrateNullable, that.migrateNullable)
            && Objects.equals(useNullableAnnotation, that.useNullableAnnotation)
            && Objects.equals(nullableAnnotation, that.nullableAnnotation)
            && Objects.equals(migrateExclusiveMinMax, that.migrateExclusiveMinMax)
            && Objects.equals(migrateExamples, that.migrateExamples)
            && Objects.equals(migrateSingleType, that.migrateSingleType)
            && Objects.equals(addJSpecifyDependency, that.addJSpecifyDependency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enableOpenApi31Properties, migrateNullable, useNullableAnnotation,
            nullableAnnotation, migrateExclusiveMinMax, migrateExamples, migrateSingleType,
            addJSpecifyDependency);
    }
}
