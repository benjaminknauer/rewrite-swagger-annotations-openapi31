package io.github.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SchemaTypeToTypesArrayRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SchemaTypeToTypesArrayRecipe())
            .parser(JavaParser.fromJavaVersion()
                .classpath("swagger-annotations-jakarta"));
    }

    // -------------------------------------------------------------------------
    // Basic conversions
    // -------------------------------------------------------------------------

    @Test
    void typeStringBecomesTypesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(type = "string")
                    private String name;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"string"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void typeIntegerBecomesTypesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(type = "integer")
                    private int count;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"integer"})
                    private int count;
                }
                """
            )
        );
    }

    @Test
    void typeWithDescriptionPreservesOtherAttributes() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "ISO 4217 code", type = "string", minLength = 3, maxLength = 3)
                    private String currency;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "ISO 4217 code", minLength = 3, maxLength = 3, types = {"string"})
                    private String currency;
                }
                """
            )
        );
    }

    @Test
    void typeOnMethodParameterIsConverted() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    void setCode(@Schema(type = "string") String code) {}
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    void setCode(@Schema(types = {"string"}) String code) {}
                }
                """
            )
        );
    }

    // -------------------------------------------------------------------------
    // No-op cases
    // -------------------------------------------------------------------------

    @Test
    void existingTypesArrayRemainsUnchanged() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"string"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void schemaWithoutTypeRemainsUnchanged() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "A name")
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void typeWithNullableTrueIsSkipped() {
        // nullable=true combination is handled by NullableSchemaRecipe / SchemaToJSpecifyNullableRecipe
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(type = "string", nullable = true)
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void alreadyMigratedTypesArrayIsIdempotent() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"string", "null"})
                    private String name;
                }
                """
            )
        );
    }
}
