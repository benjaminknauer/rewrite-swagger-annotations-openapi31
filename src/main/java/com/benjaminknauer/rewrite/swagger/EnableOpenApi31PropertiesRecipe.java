package com.benjaminknauer.rewrite.swagger;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.AddProperty;
import org.openrewrite.properties.ChangePropertyValue;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.List;

/**
 * OpenRewrite-Rezept: Aktiviert OpenAPI 3.1 in springdoc-openapi 2.x.
 *
 * <p>springdoc-openapi 2.x exportiert standardmäßig OpenAPI 3.0. Um OpenAPI 3.1
 * zu aktivieren, muss in {@code application.properties} folgendes gesetzt werden:</p>
 * <pre>springdoc.api-docs.version=openapi_3_1</pre>
 *
 * <p>Dieses Rezept:</p>
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

    @Override
    public String getDisplayName() {
        return "Aktiviere OpenAPI 3.1 in springdoc application.properties";
    }

    @Override
    public String getDescription() {
        return "Setzt 'springdoc.api-docs.version=openapi_3_1' in application.properties. "
            + "Fügt den Eintrag hinzu falls er fehlt; aktualisiert ihn falls er auf 3.0 steht.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
            // Bestehenden 3.0-Eintrag auf 3.1 aktualisieren
            new ChangePropertyValue(
                PROPERTY_KEY,
                OA_31_VALUE,
                OA_30_VALUE,
                false,
                null
            ),
            // Fehlenden Eintrag hinzufügen
            new AddProperty(
                PROPERTY_KEY,
                OA_31_VALUE,
                null,
                null
            )
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Logik erfolgt über getRecipeList(); kein eigener Visitor nötig
        return TreeVisitor.noop();
    }
}
