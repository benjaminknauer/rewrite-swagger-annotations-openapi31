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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * OpenRewrite recipe: migrates the legacy pattern
 * {@code @Schema(minimum = "X", exclusiveMinimum = true)} to the
 * swagger-core-v3 / OpenAPI 3.1 attributes
 * {@code @Schema(exclusiveMinimumValue = X)} (int).
 *
 * <p>Background: In OpenAPI 3.0, {@code exclusiveMinimum} and
 * {@code exclusiveMaximum} were boolean flags combined with {@code minimum}
 * and {@code maximum} respectively. In OpenAPI 3.1 (JSON Schema
 * 2020-12) they are numeric values. swagger-annotations 2.2.x models
 * this with the new {@code int} attributes
 * {@code exclusiveMinimumValue} and {@code exclusiveMaximumValue}.</p>
 *
 * <p>Transformation rules:</p>
 * <ul>
 *   <li>{@code minimum = "X", exclusiveMinimum = true}
 *       → {@code exclusiveMinimumValue = X}</li>
 *   <li>{@code maximum = "X", exclusiveMaximum = true}
 *       → {@code exclusiveMaximumValue = X}</li>
 *   <li>Non-integer values (e.g. {@code "1.5"}) are skipped —
 *       {@code exclusiveMinimumValue} is {@code int}.</li>
 *   <li>{@code exclusiveMinimum = false} or missing {@code minimum}
 *       → no change.</li>
 * </ul>
 */
class ExclusiveMinMaxRecipe extends Recipe {

    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migrate exclusiveMinimum/exclusiveMaximum to OpenAPI 3.1 int attributes";
    }

    @Override
    public String getDescription() {
        return "Replaces the OpenAPI 3.0 pattern '@Schema(minimum = \"X\", exclusiveMinimum = true)' "
            + "with the OpenAPI 3.1 compliant '@Schema(exclusiveMinimumValue = X)' (int attribute). "
            + "Analogously for maximum/exclusiveMaximum → exclusiveMaximumValue. "
            + "Non-integer values (e.g. '1.5') are skipped because exclusiveMinimumValue is an int.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("openapi", "swagger", "springdoc", "migration");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
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

            boolean hasExclusiveMinTrue = findBooleanArg(args, "exclusiveMinimum", true).isPresent();
            boolean hasExclusiveMaxTrue = findBooleanArg(args, "exclusiveMaximum", true).isPresent();

            if (!hasExclusiveMinTrue && !hasExclusiveMaxTrue) {
                return visited;
            }

            // Parse integer value from minimum/maximum
            OptionalInt minValue = hasExclusiveMinTrue
                ? parseIntArg(args, "minimum") : OptionalInt.empty();
            OptionalInt maxValue = hasExclusiveMaxTrue
                ? parseIntArg(args, "maximum") : OptionalInt.empty();

            // Abort if no corresponding minimum/maximum is present
            // or if the value is non-integer (exclusiveMinimumValue is int)
            if (hasExclusiveMinTrue && minValue.isEmpty()) {
                return visited;
            }
            if (hasExclusiveMaxTrue && maxValue.isEmpty()) {
                return visited;
            }

            // Attributes to remove: minimum, exclusiveMinimum (boolean flag)
            // and analogously for maximum
            List<Expression> remaining = new ArrayList<>();
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment) {
                    String key = extractKey(assignment);
                    if (hasExclusiveMinTrue && ("minimum".equals(key) || "exclusiveMinimum".equals(key))) {
                        continue;
                    }
                    if (hasExclusiveMaxTrue && ("maximum".equals(key) || "exclusiveMaximum".equals(key))) {
                        continue;
                    }
                }
                remaining.add(arg);
            }

            // New int attributes for OA 3.1
            List<String> newArgs = new ArrayList<>();
            if (hasExclusiveMinTrue && minValue.isPresent()) {
                newArgs.add(String.format("exclusiveMinimumValue = %d", minValue.getAsInt()));
            }
            if (hasExclusiveMaxTrue && maxValue.isPresent()) {
                newArgs.add(String.format("exclusiveMaximumValue = %d", maxValue.getAsInt()));
            }

            String argString = buildArgString(remaining, newArgs);
            J.Annotation newAnnotation = JavaTemplate
                .builder("@Schema(" + argString + ")")
                .imports(SCHEMA_FQN)
                .javaParser(JavaParser.fromJavaVersion()
                    .classpathFromResources(ctx, "swagger-annotations-jakarta"))
                .build()
                .apply(getCursor(), visited.getCoordinates().replace());

            return newAnnotation;
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
                            return OptionalInt.empty(); // e.g. "1.5" → skip
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

        private String buildArgString(List<Expression> remaining, List<String> newArgs) {
            StringBuilder sb = new StringBuilder();
            for (Expression expr : remaining) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(expr.print(getCursor()).strip());
            }
            for (String newArg : newArgs) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(newArg);
            }
            return sb.toString();
        }
    }
}
