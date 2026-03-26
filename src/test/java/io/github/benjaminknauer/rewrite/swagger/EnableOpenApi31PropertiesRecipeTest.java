package io.github.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class EnableOpenApi31PropertiesRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EnableOpenApi31PropertiesRecipe());
    }

    // =========================================================================
    // application.properties
    // =========================================================================

    @Test
    void missingEntryIsAdded() {
        rewriteRun(
            properties(
                """
                spring.application.name=my-app
                """,
                """
                spring.application.name=my-app
                springdoc.api-docs.version=openapi_3_1
                """
            )
        );
    }

    @Test
    void existing30EntryIsUpdated() {
        rewriteRun(
            properties(
                """
                spring.application.name=my-app
                springdoc.api-docs.version=openapi_3_0
                """,
                """
                spring.application.name=my-app
                springdoc.api-docs.version=openapi_3_1
                """
            )
        );
    }

    @Test
    void existing31EntryRemainsUnchanged() {
        rewriteRun(
            properties(
                """
                springdoc.api-docs.version=openapi_3_1
                """
            )
        );
    }

    @Test
    void emptyPropertiesFileReceivesEntry() {
        rewriteRun(
            properties(
                "",
                """
                springdoc.api-docs.version=openapi_3_1
                """
            )
        );
    }

    // =========================================================================
    // application.yml
    // =========================================================================

    @Test
    void yaml_existing30EntryIsUpdated() {
        rewriteRun(
            yaml(
                """
                spring:
                  application:
                    name: my-app
                springdoc:
                  api-docs:
                    version: openapi_3_0
                """,
                """
                spring:
                  application:
                    name: my-app
                springdoc:
                  api-docs:
                    version: openapi_3_1
                """,
                spec -> spec.path("src/main/resources/application.yml")
            )
        );
    }

    @Test
    void yaml_missingEntryIsAdded() {
        rewriteRun(
            yaml(
                """
                springdoc:
                  api-docs:
                    enabled: true
                """,
                """
                springdoc:
                  api-docs:
                    enabled: true
                    version: openapi_3_1
                """,
                spec -> spec.path("src/main/resources/application.yml")
            )
        );
    }

    @Test
    void yaml_existing31EntryRemainsUnchanged() {
        rewriteRun(
            yaml(
                """
                springdoc:
                  api-docs:
                    version: openapi_3_1
                """,
                spec -> spec.path("src/main/resources/application.yml")
            )
        );
    }

}
