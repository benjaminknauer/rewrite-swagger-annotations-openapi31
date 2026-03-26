package io.github.benjaminknauer.rewrite.swagger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Option;
import org.openrewrite.Recipe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OpenRewrite composite recipe: complete, configurable migration of
 * springdoc-openapi 2.x (OpenAPI 3.0) to OpenAPI 3.1.
 *
 * <p>All sub-recipes can be individually enabled or disabled via {@code @Option}.
 * Default (when an option is {@code null}): <strong>enabled</strong>.</p>
 *
 * <p>Nullable strategy: {@code useJSpecifyNullable=true} (the default) uses
 * {@link SchemaToJSpecifyNullableRecipe}; {@code useJSpecifyNullable=false} uses
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
 *       useJSpecifyNullable: false
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
            + "'useJSpecifyNullable'. OpenAPI 3.1 / JSON Schema 2020-12 no longer has 'nullable'. "
            + "Set to 'false' to skip nullable migration entirely.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean migrateNullable;

    @Option(
        displayName = "Use @Nullable (JSpecify) for nullable fields without explicit type",
        description = "When 'migrateNullable' is true: use @org.jspecify.annotations.Nullable "
            + "for fields without an explicit 'type' attribute in @Schema. Fields with an explicit "
            + "'type' always get @Schema(types={\"T\",\"null\"}). "
            + "Adds org.jspecify:jspecify to pom.xml when @Nullable is introduced (idempotent). "
            + "Set to 'false' to use @Schema(types={\"T\",\"null\"}) for all fields instead. "
            + "Default: true.",
        example = "true",
        required = false
    )
    @Nullable
    private final Boolean useJSpecifyNullable;

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

    /** All sub-recipes enabled by default; nullable strategy: JSpecify. */
    public SpringdocOpenApi31Recipe() {
        this(null, null, null, null, null);
    }

    @JsonCreator
    public SpringdocOpenApi31Recipe(
        @JsonProperty("enableOpenApi31Properties") @Nullable Boolean enableOpenApi31Properties,
        @JsonProperty("migrateNullable") @Nullable Boolean migrateNullable,
        @JsonProperty("useJSpecifyNullable") @Nullable Boolean useJSpecifyNullable,
        @JsonProperty("migrateExclusiveMinMax") @Nullable Boolean migrateExclusiveMinMax,
        @JsonProperty("migrateExamples") @Nullable Boolean migrateExamples
    ) {
        this.enableOpenApi31Properties = enableOpenApi31Properties;
        this.migrateNullable = migrateNullable;
        this.useJSpecifyNullable = useJSpecifyNullable;
        this.migrateExclusiveMinMax = migrateExclusiveMinMax;
        this.migrateExamples = migrateExamples;
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
    public List<Recipe> getRecipeList() {
        List<Recipe> recipes = new ArrayList<>();
        if (isEnabled(enableOpenApi31Properties)) recipes.add(new EnableOpenApi31PropertiesRecipe());
        if (isEnabled(migrateNullable)) {
            if (isEnabled(useJSpecifyNullable)) recipes.add(new SchemaToJSpecifyNullableRecipe());
            else                                recipes.add(new NullableSchemaRecipe());
        }
        if (isEnabled(migrateExclusiveMinMax))    recipes.add(new ExclusiveMinMaxRecipe());
        if (isEnabled(migrateExamples))           recipes.add(new ExampleMigrationRecipe());
        return recipes;
    }

    /** {@code null} or {@code true} → enabled; {@code false} → disabled. */
    private boolean isEnabled(@Nullable Boolean option) {
        return option == null || option;
    }
}
