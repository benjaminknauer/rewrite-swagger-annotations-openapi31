package io.github.benjaminknauer.rewrite.swagger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;

/**
 * Integrationstests für das Composite-Rezept {@link SpringdocOpenApi31Recipe}.
 * Prüft sowohl das Vollszenario als auch die selektive Aktivierung/Deaktivierung
 * einzelner Sub-Rezepte via Konfigurationsoptionen.
 */
class SpringdocOpenApi31RecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringdocOpenApi31Recipe())
            .parser(JavaParser.fromJavaVersion()
                .classpath("swagger-annotations-jakarta"));
    }

    // =========================================================================
    // Vollständiges Migrationsszenario (alle Sub-Rezepte aktiv)
    // =========================================================================

    @Test
    void vollstaendigesMigrationsSzenario() {
        rewriteRun(
            properties(
                """
                spring.application.name=zeit-tracker
                springdoc.api-docs.version=openapi_3_0
                """,
                """
                spring.application.name=zeit-tracker
                springdoc.api-docs.version=openapi_3_1
                """
            ),
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class ZeitEintragDto {
                    @Schema(description = "Projektname", nullable = true)
                    private String projekt;

                    @Schema(type = "string", nullable = true)
                    private String notiz;

                    @Schema(description = "Dauer in Minuten", example = "60")
                    private int dauer;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class ZeitEintragDto {
                    @Schema(description = "Projektname", types = {"string", "null"})
                    private String projekt;

                    @Schema(types = {"string", "null"})
                    private String notiz;

                    @Schema(description = "Dauer in Minuten", examples = {"60"})
                    private int dauer;
                }
                """
            )
        );
    }

    @Test
    void exclusiveMinMaxWirdEbenfallsMigriert() {
        rewriteRun(
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class StundenDto {
                    @Schema(minimum = "0", exclusiveMinimum = true, maximum = "24", exclusiveMaximum = true)
                    private int stunden;
                }
                """,
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class StundenDto {
                    @Schema(exclusiveMinimumValue = 0, exclusiveMaximumValue = 24)
                    private int stunden;
                }
                """
            )
        );
    }

    @Test
    void bereitsMigrierterCodeBleibtUnveraendert() {
        rewriteRun(
            properties(
                """
                springdoc.api-docs.version=openapi_3_1
                """
            ),
            java(
                """
                import io.swagger.v3.oas.annotations.media.Schema;

                class BereitsMigriert {
                    @Schema(types = {"string", "null"})
                    private String name;
                }
                """
            )
        );
    }

    // =========================================================================
    // Konfiguration: einzelne Sub-Rezepte deaktivieren
    // =========================================================================

    @Nested
    class KonfigurierteSubrezepte {

        @Test
        void migrateExamplesFalse_exampleBleibtUnveraendert() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(null, null, null, false))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(example = "Max")
                        private String name;
                    }
                    """
                    // kein zweites Argument → keine Änderung erwartet
                )
            );
        }

        @Test
        void migrateNullableFalse_nullableBleibtUnveraendert() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(null, false, null, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true)
                        private String name;
                    }
                    """
                    // nullable bleibt, aber example würde migriert (hier kein example vorhanden)
                )
            );
        }

        @Test
        void migrateExclusiveMinMaxFalse_legacyPatternBleibtUnveraendert() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(null, null, false, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(minimum = "0", exclusiveMinimum = true)
                        private int wert;
                    }
                    """
                    // exclusiveMinimum-Pattern bleibt unverändert
                )
            );
        }

        @Test
        void enablePropertiesFalse_propertiesDateiBleibtUnveraendert() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, null, null, null))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                properties(
                    """
                    spring.application.name=meine-app
                    springdoc.api-docs.version=openapi_3_0
                    """
                    // Version bleibt auf 3_0, da Properties-Rezept deaktiviert
                )
            );
        }

        @Test
        void alleDeaktiviert_keineAenderung() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, false, false, false))
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
                        private String kuerzel;
                    }
                    """
                )
            );
        }

        @Test
        void nurExamplesAktiv_nurExampleWirdMigriert() {
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, false, false, true))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true, example = "Max")
                        private String name;
                    }
                    """,
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true, examples = {"Max"})
                        private String name;
                    }
                    """
                )
            );
        }

        @Test
        void nurNullableAktiv_nurNullableWirdMigriert() {
            // nullable wird entfernt, example bleibt als remaining arg (kommt zuerst),
            // types = {"string", "null"} wird am Ende angehängt
            rewriteRun(
                spec -> spec.recipe(new SpringdocOpenApi31Recipe(false, true, false, false))
                    .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta")),
                java(
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(nullable = true, example = "Max")
                        private String name;
                    }
                    """,
                    """
                    import io.swagger.v3.oas.annotations.media.Schema;

                    class Dto {
                        @Schema(example = "Max", types = {"string", "null"})
                        private String name;
                    }
                    """
                )
            );
        }
    }
}
