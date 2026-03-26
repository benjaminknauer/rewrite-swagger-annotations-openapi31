package io.github.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * Migrates the OpenAPI 3.0 pattern
 *   @Schema(minimum = "X", exclusiveMinimum = true)
 * to the swagger-core-v3 / OpenAPI 3.1 attribute
 *   @Schema(exclusiveMinimumValue = X)  (int)
 *
 * Analogously for maximum/exclusiveMaximum → exclusiveMaximumValue.
 */
class ExclusiveMinMaxRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExclusiveMinMaxRecipe())
            .parser(JavaParser.fromJavaVersion()
                .classpath("swagger-annotations-jakarta"));
    }

    @Test
    void exclusiveMinimumWithMinimumIsCombined() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(minimum = "10", exclusiveMinimum = true)
                    private int age;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(exclusiveMinimumValue = 10)
                    private int age;
                }
                """
            )
        );
    }

    @Test
    void exclusiveMaximumWithMaximumIsCombined() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(maximum = "100", exclusiveMaximum = true)
                    private int age;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(exclusiveMaximumValue = 100)
                    private int age;
                }
                """
            )
        );
    }

    @Test
    void bothExclusiveFlagsAreCombined() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(minimum = "0", exclusiveMinimum = true, maximum = "100", exclusiveMaximum = true)
                    private int percent;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(exclusiveMinimumValue = 0, exclusiveMaximumValue = 100)
                    private int percent;
                }
                """
            )
        );
    }

    @Test
    void exclusiveFalseRemainsUnchanged() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(minimum = "0", exclusiveMinimum = false)
                    private int value;
                }
                """
            )
        );
    }

    @Test
    void minimumOnlyWithoutExclusiveRemainsUnchanged() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(minimum = "0")
                    private int value;
                }
                """
            )
        );
    }

    @Test
    void exclusiveMinimumValueAlreadyPresentRemainsUnchanged() {
        // Already migrated code (exclusiveMinimumValue as int) is left untouched
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(exclusiveMinimumValue = 10)
                    private int age;
                }
                """
            )
        );
    }

    @Test
    void nonIntegerMinimumValueIsSkipped() {
        // "1.5" cannot be converted to int → no change
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(minimum = "1.5", exclusiveMinimum = true)
                    private double value;
                }
                """
            )
        );
    }

    @Test
    void exclusiveMinimumTrueWithoutMinimumRemainsUnchanged() {
        // exclusiveMinimum=true without minimum → no corresponding value to convert
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(exclusiveMinimum = true)
                    private int value;
                }
                """
            )
        );
    }
}
