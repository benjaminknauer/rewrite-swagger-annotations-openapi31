package com.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ExampleMigrationRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExampleMigrationRecipe())
            .parser(JavaParser.fromJavaVersion()
                .classpath("swagger-annotations-jakarta"));
    }

    @Test
    void exampleAlleinWirdZuExamplesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(example = "Max Mustermann")
                    private String name;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(examples = {"Max Mustermann"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void exampleMitBeschreibungBehaeltBeschreibung() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(description = "Der vollständige Name", example = "Max Mustermann")
                    private String name;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(description = "Der vollständige Name", examples = {"Max Mustermann"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void exampleMitTypeBehaeltType() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(type = "string", example = "max@example.com")
                    private String email;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(type = "string", examples = {"max@example.com"})
                    private String email;
                }
                """
            )
        );
    }

    @Test
    void leererExampleStringWirdMigriert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(example = "")
                    private String wert;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(examples = {""})
                    private String wert;
                }
                """
            )
        );
    }

    @Test
    void schemaOhneExampleBleibtUnveraendert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(description = "Eine Beschreibung")
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void bereitsExamplesVorhandenBleibtUnveraendert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(examples = {"Max Mustermann", "Erika Musterfrau"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void exampleAufMethodenparameterWirdMigriert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    void setName(@Schema(example = "Max") String name) {}
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    void setName(@Schema(examples = {"Max"}) String name) {}
                }
                """
            )
        );
    }

    @Test
    void exampleMitMehrerenAttributenBleibtVollstaendig() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(description = "Alter in Jahren", minimum = "0", maximum = "150", example = "30")
                    private int alter;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(description = "Alter in Jahren", minimum = "0", maximum = "150", examples = {"30"})
                    private int alter;
                }
                """
            )
        );
    }
}
