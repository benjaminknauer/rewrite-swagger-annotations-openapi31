package com.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;

class EnableOpenApi31PropertiesRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EnableOpenApi31PropertiesRecipe());
    }

    @Test
    void fehlenderEintragWirdHinzugefuegt() {
        rewriteRun(
            properties(
                """
                spring.application.name=meine-app
                """,
                """
                spring.application.name=meine-app
                springdoc.api-docs.version=openapi_3_1
                """
            )
        );
    }

    @Test
    void vorhandener30EintragWirdAktualisiert() {
        rewriteRun(
            properties(
                """
                spring.application.name=meine-app
                springdoc.api-docs.version=openapi_3_0
                """,
                """
                spring.application.name=meine-app
                springdoc.api-docs.version=openapi_3_1
                """
            )
        );
    }

    @Test
    void bereits31EintragBleibtUnveraendert() {
        rewriteRun(
            properties(
                """
                springdoc.api-docs.version=openapi_3_1
                """
            )
        );
    }

    @Test
    void leerePropertiesDateiErhaeltEintrag() {
        rewriteRun(
            properties(
                "",
                """
                springdoc.api-docs.version=openapi_3_1
                """
            )
        );
    }
}
