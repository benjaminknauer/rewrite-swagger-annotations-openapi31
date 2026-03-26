package io.github.benjaminknauer.rewrite.swagger;

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
    void exampleAloneBecomesExamplesArray() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(example = "John Doe")
                    private String name;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(examples = {"John Doe"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void exampleWithDescriptionKeepsDescription() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "The full name", example = "John Doe")
                    private String name;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "The full name", examples = {"John Doe"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void exampleWithTypeKeepsType() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(type = "string", example = "john@example.com")
                    private String email;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(type = "string", examples = {"john@example.com"})
                    private String email;
                }
                """
            )
        );
    }

    @Test
    void emptyExampleStringIsMigrated() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(example = "")
                    private String value;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(examples = {""})
                    private String value;
                }
                """
            )
        );
    }

    @Test
    void schemaWithoutExampleRemainsUnchanged() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "A description")
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void existingExamplesRemainsUnchanged() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(examples = {"John Doe", "Jane Doe"})
                    private String name;
                }
                """
            )
        );
    }

    @Test
    void exampleOnMethodParameterIsMigrated() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    void setName(@Schema(example = "John") String name) {}
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    void setName(@Schema(examples = {"John"}) String name) {}
                }
                """
            )
        );
    }

    @Test
    void exampleWithTextBlockIsMigrated() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(implementation = Example.class, example = \"""
                            {
                              "id": 1
                            }\""")
                    private String value;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(implementation = Example.class, examples = {\"""
                            {
                              "id": 1
                            }\"""})
                    private String value;
                }
                """
            )
        );
    }

    @Test
    void exampleWithMultipleAttributesRemainsComplete() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "Age in years", minimum = "0", maximum = "150", example = "30")
                    private int age;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class Example {
                    @Schema(description = "Age in years", minimum = "0", maximum = "150", examples = {"30"})
                    private int age;
                }
                """
            )
        );
    }
}
