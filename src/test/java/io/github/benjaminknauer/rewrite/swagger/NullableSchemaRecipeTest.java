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

    @Test
    void nullableAlleineWirdZuTypesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(nullable = true)
                    private String name;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(types = {"string", "null"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void nullableMitTypeWirdZuTypesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(type = "string", nullable = true)
                    private String email;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(types = {"string", "null"})
                    private String email;
                }
                """
            )
        );
    }

    @Test
    void nullableMitIntegerTypeWirdZuTypesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(type = "integer", nullable = true)
                    private Integer alter;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(types = {"integer", "null"})
                    private Integer alter;
                }
                """
            )
        );
    }

    @Test
    void nullableFalseBleibtUnveraendert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(nullable = false)
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void schemaOhneNullableBleibtUnveraendert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(description = "Der Name")
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void nullableMitBeschreibungBehaltBeschreibung() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(description = "Der Name", nullable = true)
                    private String name;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(description = "Der Name", types = {"string", "null"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void nullableAufKlassenfeldOhneTyp() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(nullable = true)
                    private Boolean aktiv;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(types = {"string", "null"})
                    private Boolean aktiv;
                }
                """
            )
        );
    }
}
