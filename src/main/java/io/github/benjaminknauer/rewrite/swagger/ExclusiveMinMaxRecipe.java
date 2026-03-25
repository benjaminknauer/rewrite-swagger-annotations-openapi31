package io.github.benjaminknauer.rewrite.swagger;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * OpenRewrite-Rezept: Migriert das Legacy-Pattern
 * {@code @Schema(minimum = "X", exclusiveMinimum = true)} auf die
 * swagger-core-v3 / OpenAPI-3.1-Attribute
 * {@code @Schema(exclusiveMinimumValue = X)} (int).
 *
 * <p>Hintergrund: In OpenAPI 3.0 waren {@code exclusiveMinimum} und
 * {@code exclusiveMaximum} Boolean-Flags, die zusammen mit {@code minimum}
 * bzw. {@code maximum} kombiniert wurden. In OpenAPI 3.1 (JSON Schema
 * 2020-12) sind sie numerische Werte. swagger-annotations 2.2.x bildet
 * das mit den neuen {@code int}-Attributen
 * {@code exclusiveMinimumValue} und {@code exclusiveMaximumValue} ab.</p>
 *
 * <p>Transformationsregeln:</p>
 * <ul>
 *   <li>{@code minimum = "X", exclusiveMinimum = true}
 *       → {@code exclusiveMinimumValue = X}</li>
 *   <li>{@code maximum = "X", exclusiveMaximum = true}
 *       → {@code exclusiveMaximumValue = X}</li>
 *   <li>Nicht ganzzahlige Werte (z.&nbsp;B. {@code "1.5"}) werden
 *       übersprungen — {@code exclusiveMinimumValue} ist {@code int}.</li>
 *   <li>{@code exclusiveMinimum = false} oder fehlendes {@code minimum}
 *       → keine Änderung.</li>
 * </ul>
 */
public class ExclusiveMinMaxRecipe extends Recipe {

    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migriere exclusiveMinimum/exclusiveMaximum auf OpenAPI 3.1 int-Attribute";
    }

    @Override
    public String getDescription() {
        return "Ersetzt das OA-3.0-Pattern 'minimum + exclusiveMinimum = true' durch "
            + "das swagger-core-v3-konforme 'exclusiveMinimumValue = X' (int). "
            + "Nicht ganzzahlige Werte werden übersprungen.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ExclusiveMinMaxVisitor();
    }

    private static class ExclusiveMinMaxVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation visited = super.visitAnnotation(annotation, ctx);

            if (!isSchemaAnnotation(visited)) {
                return visited;
            }

            List<Expression> args = visited.getArguments();
            if (args == null || args.isEmpty()) {
                return visited;
            }

            boolean hatExclusiveMinTrue = findBooleanArg(args, "exclusiveMinimum", true).isPresent();
            boolean hatExclusiveMaxTrue = findBooleanArg(args, "exclusiveMaximum", true).isPresent();

            if (!hatExclusiveMinTrue && !hatExclusiveMaxTrue) {
                return visited;
            }

            // Ganzzahligen Wert aus minimum/maximum parsen
            OptionalInt minWert = hatExclusiveMinTrue
                ? parseIntArg(args, "minimum") : OptionalInt.empty();
            OptionalInt maxWert = hatExclusiveMaxTrue
                ? parseIntArg(args, "maximum") : OptionalInt.empty();

            // Abbrechen wenn kein korrespondierendes minimum/maximum vorhanden
            // oder der Wert nicht ganzzahlig ist (exclusiveMinimumValue ist int)
            if (hatExclusiveMinTrue && minWert.isEmpty()) {
                return visited;
            }
            if (hatExclusiveMaxTrue && maxWert.isEmpty()) {
                return visited;
            }

            // Zu entfernende Attribute: minimum, exclusiveMinimum (boolean-Flag)
            // und analog für maximum
            List<Expression> verbleibend = new ArrayList<>();
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment) {
                    String key = extractKey(assignment);
                    if (hatExclusiveMinTrue && ("minimum".equals(key) || "exclusiveMinimum".equals(key))) {
                        continue;
                    }
                    if (hatExclusiveMaxTrue && ("maximum".equals(key) || "exclusiveMaximum".equals(key))) {
                        continue;
                    }
                }
                verbleibend.add(arg);
            }

            // Neue int-Attribute für OA 3.1
            List<String> neueArgs = new ArrayList<>();
            if (hatExclusiveMinTrue && minWert.isPresent()) {
                neueArgs.add(String.format("exclusiveMinimumValue = %d", minWert.getAsInt()));
            }
            if (hatExclusiveMaxTrue && maxWert.isPresent()) {
                neueArgs.add(String.format("exclusiveMaximumValue = %d", maxWert.getAsInt()));
            }

            String argString = buildArgString(verbleibend, neueArgs);
            J.Annotation neueAnnotation = JavaTemplate
                .builder("@Schema(" + argString + ")")
                .imports(SCHEMA_FQN)
                .javaParser(JavaParser.fromJavaVersion()
                    .classpath("swagger-annotations-jakarta"))
                .build()
                .apply(getCursor(), visited.getCoordinates().replace());

            return neueAnnotation;
        }

        private boolean isSchemaAnnotation(J.Annotation annotation) {
            if (annotation.getType() != null) {
                return TypeUtils.isAssignableTo(SCHEMA_FQN, annotation.getType());
            }
            return "Schema".equals(annotation.getSimpleName());
        }

        private Optional<Boolean> findBooleanArg(List<Expression> args, String key, boolean expected) {
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment && key.equals(extractKey(assignment))) {
                    Expression value = assignment.getAssignment();
                    if (value instanceof J.Literal literal
                            && literal.getValue() instanceof Boolean b && b == expected) {
                        return Optional.of(b);
                    }
                }
            }
            return Optional.empty();
        }

        private OptionalInt parseIntArg(List<Expression> args, String key) {
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment && key.equals(extractKey(assignment))) {
                    Expression value = assignment.getAssignment();
                    if (value instanceof J.Literal literal && literal.getValue() instanceof String s) {
                        try {
                            return OptionalInt.of(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return OptionalInt.empty(); // z.B. "1.5" → überspringen
                        }
                    }
                }
            }
            return OptionalInt.empty();
        }

        private String extractKey(J.Assignment assignment) {
            Expression variable = assignment.getVariable();
            return variable instanceof J.Identifier id ? id.getSimpleName() : "";
        }

        private String buildArgString(List<Expression> verbleibend, List<String> neueArgs) {
            StringBuilder sb = new StringBuilder();
            for (Expression expr : verbleibend) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(expr.print(getCursor()).strip());
            }
            for (String neuerArg : neueArgs) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(neuerArg);
            }
            return sb.toString();
        }
    }
}
