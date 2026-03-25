package io.github.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SchemaToJSpecifyNullableRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SchemaToJSpecifyNullableRecipe())
            .parser(JavaParser.fromJavaVersion()
                .classpath("swagger-annotations-jakarta", "jspecify"));
    }

    // =========================================================================
    // Explicit type= attribute — convert to @Schema(types={"X","null"})
    // =========================================================================

    @Nested
    class ExplicitTypeConversion {

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

        @Test
        void nullableWithExplicitTypeAndDescriptionKeepsDescription() {
            rewriteRun(
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Example {
                        @Schema(type = "string", nullable = true, description = "ISO code")
                        private String code;
                    }
                    """,
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Example {
                        @Schema(description = "ISO code", types = {"string", "null"})
                        private String code;
                    }
                    """
                )
            );
        }
    }

    // =========================================================================
    // No explicit type= — replace with @Nullable (JSpecify)
    // =========================================================================

    @Nested
    class NoExplicitTypeJSpecify {

        @Test
        void nullableOnlySchemaIsReplacedWithNullable() {
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
                    import org.jspecify.annotations.Nullable;

                    class Example {
                        @Nullable
                        private String name;
                    }
                    """
                )
            );
        }

        @Test
        void nullableOnlyOnIntegerField() {
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
                    import org.jspecify.annotations.Nullable;

                    class Example {
                        @Nullable
                        private Integer count;
                    }
                    """
                )
            );
        }

        @Test
        void nullableWithDescriptionKeepsDescription() {
            rewriteRun(
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Example {
                        @Schema(nullable = true, description = "The user's name")
                        private String name;
                    }
                    """,
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;
                    import org.jspecify.annotations.Nullable;

                    class Example {
                        @Nullable
                        @Schema(description = "The user's name")
                        private String name;
                    }
                    """
                )
            );
        }
    }

    // =========================================================================
    // Idempotency
    // =========================================================================

    @Nested
    class Idempotency {

        @Test
        void alreadyNullableAnnotationWithNoTypeSchemaIsNotDoubled() {
            rewriteRun(
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;
                    import org.jspecify.annotations.Nullable;

                    class Example {
                        @Nullable
                        @Schema(nullable = true)
                        private String name;
                    }
                    """
                )
            );
        }
    }

    // =========================================================================
    // No-op cases
    // =========================================================================

    @Nested
    class NoOp {

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
        void noAnnotationRemainsUnchanged() {
            rewriteRun(
                java(
                    """
                    class Example {
                        private String name;
                    }
                    """
                )
            );
        }
    }
}
