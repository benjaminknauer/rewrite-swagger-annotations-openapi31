package io.github.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;

/**
 * Integration tests for the composite recipe {@link SpringdocOpenApi31Recipe}.
 * Covers the full migration scenario as well as selective enabling/disabling
 * of individual sub-recipes via configuration options.
 */
class SpringdocOpenApi31RecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringdocOpenApi31Recipe())
            .parser(JavaParser.fromJavaVersion()
                .classpath("swagger-annotations-jakarta"));
    }

    // =========================================================================
    // Full migration scenario (all sub-recipes active)
    // =========================================================================

    @Test
    void fullMigrationScenario() {
        // Default: useJSpecifyNullable=true → @Nullable for fields without explicit type=,
        //          @Schema(types={"T","null"}) for fields with explicit type=
        rewriteRun(
            properties(
                """
                spring.application.name=time-tracker
                springdoc.api-docs.version=openapi_3_0
                """,
                """
                spring.application.name=time-tracker
                springdoc.api-docs.version=openapi_3_1
                """
            ),
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class TimeEntryDto {
                    @Schema(description = "Project name", nullable = true)
                    private String project;

                    @Schema(type = "string", nullable = true)
                    private String note;

                    @Schema(description = "Duration in minutes", example = "60")
                    private int duration;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;
                import org.jspecify.annotations.Nullable;

                class TimeEntryDto {
                    @Nullable
                    @Schema(description = "Project name")
                    private String project;

                    @Schema(types = {"string", "null"})
                    private String note;

                    @Schema(description = "Duration in minutes", examples = {"60"})
                    private int duration;
                }
                """
            )
        );
    }

    @Test
    void exclusiveMinMaxIsAlsoMigrated() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class HoursDto {
                    @Schema(minimum = "0", exclusiveMinimum = true, maximum = "24", exclusiveMaximum = true)
                    private int hours;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class HoursDto {
                    @Schema(exclusiveMinimumValue = 0, exclusiveMaximumValue = 24)
                    private int hours;
                }
                """
            )
        );
    }

    @Test
    void singleTypeIsMigratedToTypesArray() {
        // type="X" without nullable → types={"X"}
        // type="X" with nullable=true → types={"X","null"} via nullable recipe (runs before)
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Dto {
                    @Schema(type = "string", description = "A code")
                    private String code;

                    @Schema(type = "string", nullable = true)
                    private String note;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Dto {
                    @Schema(types = {"string"}, description = "A code")
                    private String code;

                    @Schema(types = {"string", "null"})
                    private String note;
                }
                """
            )
        );
    }

    @Test
    void migrateSingleTypeFalse_typeRemainsUnchanged() {
        rewriteRun(
            spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, false, null, false, false, false))
                .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Dto {
                    @Schema(type = "string")
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void alreadyMigratedCodeRemainsUnchanged() {
        rewriteRun(
            properties(
                """
                springdoc.api-docs.version=openapi_3_1
                """
            ),
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class AlreadyMigrated {
                    @Schema(types = {"string", "null"})
                    private String name;
                }
                """
            )
        );
    }

    // =========================================================================
    // Configuration: disabling individual sub-recipes
    // =========================================================================

    @Nested
    class ConfiguredSubRecipes {

        @Test
        void migrateExamplesFalse_exampleRemainsUnchanged() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(null, null, null, null, false, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(example = "John")
                        private String name;
                    }
                    """
                    // no second argument → no change expected
                )
            );
        }

        @Test
        void migrateNullableFalse_nullableRemainsUnchanged() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(null, false, null, null, null, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true)
                        private String name;
                    }
                    """
                    // nullable stays, but example would be migrated (no example present here)
                )
            );
        }

        @Test
        void migrateExclusiveMinMaxFalse_legacyPatternRemainsUnchanged() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(null, null, null, false, null, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(minimum = "0", exclusiveMinimum = true)
                        private int value;
                    }
                    """
                    // exclusiveMinimum pattern remains unchanged
                )
            );
        }

        @Test
        void enablePropertiesFalse_propertiesFileRemainsUnchanged() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, null, null, null, null, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                properties(
                    """
                    spring.application.name=my-app
                    springdoc.api-docs.version=openapi_3_0
                    """
                    // Version stays at 3_0 because the properties recipe is disabled
                )
            );
        }

        @Test
        void allDisabled_noChange() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, false, false, false, false, false))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                properties(
                    """
                    springdoc.api-docs.version=openapi_3_0
                    """
                ),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true)
                        private String name;

                        @Schema(example = "foo")
                        private String code;
                    }
                    """
                )
            );
        }

        @Test
        void onlyExamplesActive_onlyExampleIsMigrated() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, false, null, false, true, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true, example = "John")
                        private String name;
                    }
                    """,
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true, examples = {"John"})
                        private String name;
                    }
                    """
                )
            );
        }

        @Test
        void onlyNullableActive_jspecify_onlyNullableIsMigrated() {
            // Default: useJSpecifyNullable=true — nullable is replaced by @Nullable,
            // remaining @Schema attributes are preserved
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, true, null, false, false, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true, example = "John")
                        private String name;
                    }
                    """,
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;
                    import org.jspecify.annotations.Nullable;

                    class Dto {
                        @Nullable
                        @Schema(example = "John")
                        private String name;
                    }
                    """
                )
            );
        }

        @Test
        void useJSpecifyNullableFalse_usesTypesArrayStrategy() {
            // useJSpecifyNullable=false → NullableSchemaRecipe — types-array in annotation
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, true, false, false, false, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true, example = "John")
                        private String name;
                    }
                    """,
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(example = "John", types = {"string", "null"})
                        private String name;
                    }
                    """
                )
            );
        }
    }
}
