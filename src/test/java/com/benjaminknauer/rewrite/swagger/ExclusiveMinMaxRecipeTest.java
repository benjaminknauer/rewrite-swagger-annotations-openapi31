package com.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * Migriert das OpenAPI-3.0-Pattern
 *   @Schema(minimum = "X", exclusiveMinimum = true)
 * auf das swagger-core-v3 / OpenAPI-3.1-Attribut
 *   @Schema(exclusiveMinimumValue = X)  (int)
 *
 * Analog für maximum/exclusiveMaximum → exclusiveMaximumValue.
 */
class ExclusiveMinMaxRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExclusiveMinMaxRecipe())
            .parser(JavaParser.fromJavaVersion()
                .classpath("swagger-annotations-jakarta"));
    }

    @Test
    void exclusiveMinimumMitMinimumWirdKombiniert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(minimum = "10", exclusiveMinimum = true)
                    private int alter;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(exclusiveMinimumValue = 10)
                    private int alter;
                }
                """
            )
        );
    }

    @Test
    void exclusiveMaximumMitMaximumWirdKombiniert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(maximum = "100", exclusiveMaximum = true)
                    private int alter;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(exclusiveMaximumValue = 100)
                    private int alter;
                }
                """
            )
        );
    }

    @Test
    void beideExclusiveWerdenKombiniert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(minimum = "0", exclusiveMinimum = true, maximum = "100", exclusiveMaximum = true)
                    private int prozent;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(exclusiveMinimumValue = 0, exclusiveMaximumValue = 100)
                    private int prozent;
                }
                """
            )
        );
    }

    @Test
    void exclusiveFalseBleibtUnveraendert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(minimum = "0", exclusiveMinimum = false)
                    private int wert;
                }
                """
            )
        );
    }

    @Test
    void nurMinimumOhneExclusiveBleibtUnveraendert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(minimum = "0")
                    private int wert;
                }
                """
            )
        );
    }

    @Test
    void exclusiveMinimumValueBereitsVorhandenBleibtUnveraendert() {
        // Bereits migrierter Code (exclusiveMinimumValue als int) bleibt unberührt
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(exclusiveMinimumValue = 10)
                    private int alter;
                }
                """
            )
        );
    }

    @Test
    void nichtGanzzahligerMinimumWertWirdUebersprungen() {
        // "1.5" kann nicht in int konvertiert werden → keine Änderung
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Beispiel {
                    @Schema(minimum = "1.5", exclusiveMinimum = true)
                    private double wert;
                }
                """
            )
        );
    }
}
