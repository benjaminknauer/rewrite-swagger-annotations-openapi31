package io.github.benjaminknauer.rewrite.swagger;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.AddProperty;
import org.openrewrite.properties.ChangePropertyValue;
import org.openrewrite.yaml.ChangeValue;
import org.openrewrite.yaml.MergeYaml;

import java.time.Duration;
import java.util.Set;

import java.util.List;

/**
 * OpenRewrite-Rezept: Aktiviert OpenAPI 3.1 in springdoc-openapi 2.x.
 *
 * <p>springdoc-openapi 2.x exportiert standardmäßig OpenAPI 3.0. Um OpenAPI 3.1
 * zu aktivieren, muss in {@code application.properties} folgendes gesetzt werden:</p>
 * <pre>springdoc.api-docs.version=openapi_3_1</pre>
 *
 * <p>Dieses Rezept unterstützt sowohl {@code application.properties} als auch
 * {@code application.yml}:</p>
 * <ul>
 *   <li>Ändert {@code openapi_3_0} → {@code openapi_3_1} falls der Eintrag bereits vorhanden ist</li>
 *   <li>Fügt den Eintrag hinzu falls er fehlt</li>
 *   <li>Lässt {@code openapi_3_1} unverändert (idempotent)</li>
 * </ul>
 */
public class EnableOpenApi31PropertiesRecipe extends Recipe {

    private static final String PROPERTY_KEY = "springdoc.api-docs.version";
    private static final String OA_31_VALUE = "openapi_3_1";
    private static final String OA_30_VALUE = "openapi_3_0";

    // YAML-Pfad: $.springdoc.api-docs.version
    private static final String YAML_KEY_PATH = "$.springdoc.api-docs.version";

    @Override
    public String getDisplayName() {
        return "Aktiviere OpenAPI 3.1 in springdoc application.properties / application.yml";
    }

    @Override
    public String getDescription() {
        return "Setzt 'springdoc.api-docs.version=openapi_3_1' in application.properties bzw. application.yml. "
            + "Aktualisiert einen vorhandenen 'openapi_3_0'-Eintrag und fügt den Schlüssel hinzu, falls er fehlt. "
            + "Bereits korrekt konfigurierte Dateien werden nicht verändert (idempotent).";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("openapi", "swagger", "springdoc", "migration");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
            // application.properties: bestehenden 3.0-Eintrag auf 3.1 aktualisieren
            new ChangePropertyValue(
                PROPERTY_KEY,
                OA_31_VALUE,
                OA_30_VALUE,
                false,
                null
            ),
            // application.properties: fehlenden Eintrag hinzufügen
            new AddProperty(
                PROPERTY_KEY,
                OA_31_VALUE,
                null,
                null
            ),
            // application.yml: bestehenden Wert ändern (openapi_3_0 → openapi_3_1)
            new ChangeValue(
                YAML_KEY_PATH,
                OA_31_VALUE,
                null
            ),
            // application.yml: fehlenden Eintrag als YAML-Fragment einfügen
            new MergeYaml(
                "$.springdoc.api-docs",
                "version: " + OA_31_VALUE,
                true,
                null,
                null
            )
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return TreeVisitor.noop();
    }
}
