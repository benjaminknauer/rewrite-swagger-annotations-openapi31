package io.github.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

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
                        @Schema(types = {"string", "null"}, description = "ISO code")
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

    // =========================================================================
    // Regression: jspecify dependency must NOT be added for explicit-type cases
    // =========================================================================

    /**
     * Regression test for the bug where jspecify was added to pom.xml even when
     * no {@code @Nullable} was introduced (only {@code type→types} conversion).
     *
     * <p>Root cause: the {@link AddDependency} constructor call in {@code getRecipeList()}
     * had the arguments in the wrong order — {@code onlyIfUsing} (position 7) was {@code null}
     * (no filter), and {@code NULLABLE_FQN} was placed at position 8 ({@code type}, the Maven
     * packaging type). With {@code onlyIfUsing = null}, {@link AddDependency} always added
     * jspecify unconditionally, regardless of whether {@code @Nullable} was actually introduced.
     *
     * <p>Fixed by passing {@code NULLABLE_FQN} at position 7 ({@code onlyIfUsing}) and
     * {@code null} at position 8 ({@code type}). The {@code onlyIfUsing} guard prevents
     * jspecify from being added when {@code @org.jspecify.annotations.Nullable} is not present
     * in the source code.</p>
     *
     * <p><strong>Note on pom.xml addition in real projects:</strong> when {@code @Nullable}
     * IS introduced, {@link AddDependency} adds jspecify in a subsequent OpenRewrite cycle
     * (cycle 2 scans the already-transformed source and finds {@code @Nullable}).
     * This multi-cycle behavior is verified by integration tests, not by unit tests.</p>
     */
    @Nested
    class JspecifyDependencyAddition {

        @Test
        void jspecifyNotAddedWhenOnlyExplicitTypeConversionHappens() {
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
                ),
                // pom.xml must remain unchanged — no jspecify dependency added
                pomXml("""
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>my-app</artifactId>
                      <version>1.0</version>
                    </project>
                    """)
            );
        }
    }

    // =========================================================================
    // Method parameters
    // =========================================================================

    @Nested
    class MethodParameters {

        @Test
        void nullableOnlyOnMethodParameterBecomesNullable() {
            rewriteRun(
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Example {
                        void setName(@Schema(nullable = true) String name) {}
                    }
                    """,
                    """
                    import org.jspecify.annotations.Nullable;

                    class Example {
                        void setName(@Nullable String name) {}
                    }
                    """
                )
            );
        }

        @Test
        void nullableWithExplicitTypeOnMethodParameterBecomesTypesArray() {
            rewriteRun(
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Example {
                        void setEmail(@Schema(type = "string", nullable = true) String email) {}
                    }
                    """,
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Example {
                        void setEmail(@Schema(types = {"string", "null"}) String email) {}
                    }
                    """
                )
            );
        }
    }
}
