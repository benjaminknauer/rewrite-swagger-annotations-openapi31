package io.github.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NullableSchemaRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NullableSchemaRecipe())
            .parser(JavaParser.fromJavaVersion()
                .classpath("swagger-annotations-jakarta"));
    }

    // -------------------------------------------------------------------------
    // Explicit type= attribute — type is taken from the annotation
    // -------------------------------------------------------------------------

    @Test
    void nullableWithExplicitTypeBecomesTypesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(type = "string", nullable = true)
                    private String email;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"string", "null"})
                    private String email;
                }
                """
            )
        );
    }

    @Test
    void nullableWithExplicitIntegerTypeBecomesTypesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(type = "integer", nullable = true)
                    private Integer age;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"integer", "null"})
                    private Integer age;
                }
                """
            )
        );
    }

    // -------------------------------------------------------------------------
    // No explicit type= — base type is inferred from the Java field type
    // -------------------------------------------------------------------------

    @Test
    void inferStringFromStringField() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(nullable = true)
                    private String name;
                }
                """,
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

    @Test
    void inferBooleanFromBooleanField() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(nullable = true)
                    private Boolean active;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"boolean", "null"})
                    private Boolean active;
                }
                """
            )
        );
    }

    @Test
    void inferIntegerFromIntegerField() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(nullable = true)
                    private Integer count;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"integer", "null"})
                    private Integer count;
                }
                """
            )
        );
    }

    @Test
    void inferIntegerFromLongField() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(nullable = true)
                    private Long id;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"integer", "null"})
                    private Long id;
                }
                """
            )
        );
    }

    @Test
    void inferNumberFromDoubleField() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(nullable = true)
                    private Double price;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(types = {"number", "null"})
                    private Double price;
                }
                """
            )
        );
    }

    // -------------------------------------------------------------------------
    // Other attributes are preserved
    // -------------------------------------------------------------------------

    @Test
    void nullableWithDescriptionKeepsDescription() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "The name", nullable = true)
                    private String name;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "The name", types = {"string", "null"})
                    private String name;
                }
                """
            )
        );
    }

    // -------------------------------------------------------------------------
    // No-op cases
    // -------------------------------------------------------------------------

    @Test
    void nullableFalseRemainsUnchanged() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(nullable = false)
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void schemaWithoutNullableRemainsUnchanged() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "The name")
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void nullableOnMethodParameterBecomesTypesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    void setName(@Schema(nullable = true) String name) {}
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    void setName(@Schema(types = {"string", "null"}) String name) {}
                }
                """
            )
        );
    }
}
