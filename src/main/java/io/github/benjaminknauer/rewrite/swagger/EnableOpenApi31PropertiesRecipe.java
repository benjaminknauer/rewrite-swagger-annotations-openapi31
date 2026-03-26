package io.github.benjaminknauer.rewrite.swagger;

import org.openrewrite.Recipe;
import org.openrewrite.RecipeList;
import org.openrewrite.properties.AddProperty;
import org.openrewrite.properties.ChangePropertyValue;
import org.openrewrite.yaml.ChangeValue;
import org.openrewrite.yaml.MergeYaml;

import java.time.Duration;
import java.util.Set;

/**
 * OpenRewrite recipe: enables OpenAPI 3.1 in springdoc-openapi 2.x.
 *
 * <p>springdoc-openapi 2.x exports OpenAPI 3.0 by default. To enable OpenAPI 3.1,
 * the following property must be set in {@code application.properties}:</p>
 * <pre>springdoc.api-docs.version=openapi_3_1</pre>
 *
 * <p>This recipe supports both {@code application.properties} and
 * {@code application.yml}:</p>
 * <ul>
 *   <li>Updates {@code openapi_3_0} → {@code openapi_3_1} if the entry already exists</li>
 *   <li>Adds the entry if it is missing</li>
 *   <li>Leaves {@code openapi_3_1} unchanged (idempotent)</li>
 * </ul>
 */
class EnableOpenApi31PropertiesRecipe extends Recipe {

    private static final String PROPERTY_KEY = "springdoc.api-docs.version";
    private static final String OA_31_VALUE = "openapi_3_1";
    private static final String OA_30_VALUE = "openapi_3_0";

    // YAML path: $.springdoc.api-docs.version
    private static final String YAML_KEY_PATH = "$.springdoc.api-docs.version";

    @Override
    public String getDisplayName() {
        return "Enable OpenAPI 3.1 in springdoc application.properties / application.yml";
    }

    @Override
    public String getDescription() {
        return "Sets 'springdoc.api-docs.version=openapi_3_1' in application.properties or application.yml. "
            + "Updates an existing 'openapi_3_0' entry and adds the key if it is missing. "
            + "Already correctly configured files are not changed (idempotent).";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("openapi", "swagger", "springdoc", "migration");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public void buildRecipeList(RecipeList recipeList) {
        // application.properties: update existing 3.0 entry to 3.1
        recipeList.recipe(new ChangePropertyValue(
            PROPERTY_KEY,
            OA_31_VALUE,
            OA_30_VALUE,
            false,
            null
        ));
        // application.properties: add missing entry
        recipeList.recipe(new AddProperty(
            PROPERTY_KEY,
            OA_31_VALUE,
            null,
            null,
            null
        ));
        // application.yml: update existing value (openapi_3_0 → openapi_3_1)
        recipeList.recipe(new ChangeValue(
            YAML_KEY_PATH,
            OA_31_VALUE,
            null
        ));
        // application.yml: insert missing entry as YAML fragment (only in application*.yml files)
        recipeList.recipe(new MergeYaml(
            "$.springdoc.api-docs",
            "version: " + OA_31_VALUE,
            true,
            null,
            "**/application*.yml",
            null,
            null,
            null
        ));
    }
}
