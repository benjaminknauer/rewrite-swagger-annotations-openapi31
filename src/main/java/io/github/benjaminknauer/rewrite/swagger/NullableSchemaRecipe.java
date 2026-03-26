package io.github.benjaminknauer.rewrite.swagger;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * OpenRewrite recipe: migrates {@code @Schema(nullable = true)} from OpenAPI 3.0
 * to the OpenAPI 3.1 compliant syntax {@code @Schema(types = {"T", "null"})}.
 *
 * <p>Background: In OpenAPI 3.1, {@code nullable} was removed in favour of JSON Schema
 * Draft 2020-12. Nullable types are now expressed as an array in the {@code type} field,
 * e.g. {@code ["string", "null"]}.</p>
 *
 * <p>Transformation rules:</p>
 * <ul>
 *   <li>{@code @Schema(type = "X", nullable = true)} → {@code @Schema(types = {"X", "null"})}</li>
 *   <li>{@code @Schema(nullable = true)} without explicit {@code type}: the base type is
 *       inferred from the Java field/parameter type (e.g. {@code Boolean} → {@code "boolean"},
 *       {@code Integer} → {@code "integer"}). Falls back to {@code "string"} for unknown types.</li>
 *   <li>All other attributes (e.g. {@code description}) are preserved.</li>
 *   <li>{@code nullable = false} is ignored (no change).</li>
 * </ul>
 */
class NullableSchemaRecipe extends Recipe {

    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migrate @Schema(nullable=true) to OpenAPI 3.1 types array";
    }

    @Override
    public String getDescription() {
        return "Replaces the OpenAPI 3.0 attribute 'nullable = true' in @Schema annotations "
            + "with the OpenAPI 3.1 compliant 'types = {\"T\", \"null\"}' array. "
            + "An existing 'type' attribute is used as the base type; if absent, the type is "
            + "inferred from the Java field or parameter type (Boolean → \"boolean\", "
            + "Integer/Long → \"integer\", etc.). Falls back to \"string\" for unknown types.";
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
        return new NullableSchemaVisitor();
    }

    private static class NullableSchemaVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final Map<String, String> JAVA_TYPE_TO_OPENAPI = Map.ofEntries(
            // Strings
            Map.entry(String.class.getName(),     "string"),
            // Booleans
            Map.entry(Boolean.class.getName(),    "boolean"),
            // Integers
            Map.entry(Integer.class.getName(),    "integer"),
            Map.entry(Long.class.getName(),       "integer"),
            Map.entry(Short.class.getName(),      "integer"),
            Map.entry(Byte.class.getName(),       "integer"),
            Map.entry(BigInteger.class.getName(), "integer"),
            // Numbers
            Map.entry(Float.class.getName(),      "number"),
            Map.entry(Double.class.getName(),     "number"),
            Map.entry(BigDecimal.class.getName(), "number"),
            // Types that Jackson always serializes as string regardless of configuration
            Map.entry(UUID.class.getName(),       "string"),
            Map.entry(URI.class.getName(),        "string"),
            Map.entry(URL.class.getName(),        "string"),
            Map.entry(Currency.class.getName(),   "string"),
            Map.entry(Locale.class.getName(),     "string")
            // NOTE: java.time types (LocalDate, LocalDateTime, etc.) are intentionally absent.
            // Their JSON representation depends on Jackson configuration:
            // WRITE_DATES_AS_TIMESTAMPS=true (Jackson default) → array/number
            // WRITE_DATES_AS_TIMESTAMPS=false (common in Spring Boot) → string
            // Inferring from the Java type alone would be incorrect in half the cases.
        );

        private static final Set<String> COLLECTION_FQNS = Set.of(
            List.class.getName(),
            ArrayList.class.getName(),
            LinkedList.class.getName(),
            Set.class.getName(),
            HashSet.class.getName(),
            LinkedHashSet.class.getName(),
            Collection.class.getName(),
            Queue.class.getName(),
            Deque.class.getName()
        );

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

            boolean hasNullableTrue = findBooleanArg(args, "nullable", true).isPresent();
            if (!hasNullableTrue) {
                return visited;
            }

            String baseType = findStringArg(args, "type").orElseGet(this::inferBaseType);

            // Keep all args except 'nullable' and 'type'
            List<Expression> remaining = new ArrayList<>();
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment) {
                    String key = extractKey(assignment);
                    if ("nullable".equals(key) || "type".equals(key)) {
                        continue;
                    }
                }
                remaining.add(arg);
            }

            // Build new types attribute as a string array literal
            String typesCode = String.format("types = {\"%s\", \"null\"}", baseType);
            return buildAnnotationWithArgs(visited, remaining, typesCode, ctx);
        }

        /**
         * Infers the JSON Schema base type from the Java field or parameter type
         * that carries the annotation. Falls back to {@code "string"} if the type
         * cannot be determined.
         */
        private String inferBaseType() {
            J.VariableDeclarations varDecls = getCursor().firstEnclosing(J.VariableDeclarations.class);
            if (varDecls != null && !varDecls.getVariables().isEmpty()) {
                JavaType javaType = varDecls.getVariables().get(0).getType();
                String mapped = mapJavaTypeToOpenApi(javaType);
                if (mapped != null) {
                    return mapped;
                }
            }
            return "string";
        }

        /**
         * Maps a resolved {@link JavaType} to its JSON Schema type string.
         * Returns {@code null} for unmappable types so the caller can fall back.
         */
        private static String mapJavaTypeToOpenApi(JavaType type) {
            if (type instanceof JavaType.Primitive primitive) {
                return switch (primitive) {
                    case Boolean -> "boolean";
                    case Int, Long, Short, Byte -> "integer";
                    case Float, Double -> "number";
                    case Char -> "string";
                    default -> null;
                };
            }
            if (type instanceof JavaType.Array) {
                return "array";
            }
            if (type instanceof JavaType.Parameterized parameterized) {
                return COLLECTION_FQNS.contains(parameterized.getType().getFullyQualifiedName())
                    ? "array" : "object";
            }
            if (type instanceof JavaType.FullyQualified fq) {
                String mapped = JAVA_TYPE_TO_OPENAPI.get(fq.getFullyQualifiedName());
                if (mapped != null) return mapped;
                if (COLLECTION_FQNS.contains(fq.getFullyQualifiedName())) return "array";
                if (fq.getKind() == JavaType.FullyQualified.Kind.Enum) return "string";
                return "object";
            }
            return null;
        }

        private boolean isSchemaAnnotation(J.Annotation annotation) {
            if (annotation.getType() == null) {
                return "Schema".equals(annotation.getSimpleName());
            }
            return TypeUtils.isAssignableTo(SCHEMA_FQN, annotation.getType());
        }

        private Optional<Boolean> findBooleanArg(List<Expression> args, String key, boolean expectedValue) {
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment) {
                    if (key.equals(extractKey(assignment))) {
                        Expression value = assignment.getAssignment();
                        if (value instanceof J.Literal literal) {
                            Object val = literal.getValue();
                            if (val instanceof Boolean b && b == expectedValue) {
                                return Optional.of(b);
                            }
                        }
                    }
                }
            }
            return Optional.empty();
        }

        private Optional<String> findStringArg(List<Expression> args, String key) {
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment) {
                    if (key.equals(extractKey(assignment))) {
                        Expression value = assignment.getAssignment();
                        if (value instanceof J.Literal literal && literal.getValue() instanceof String s) {
                            return Optional.of(s);
                        }
                    }
                }
            }
            return Optional.empty();
        }

        private String extractKey(J.Assignment assignment) {
            Expression variable = assignment.getVariable();
            if (variable instanceof J.Identifier id) {
                return id.getSimpleName();
            }
            return "";
        }

        private J.Annotation buildAnnotationWithArgs(
            J.Annotation original,
            List<Expression> remaining,
            String newArgCode,
            ExecutionContext ctx
        ) {
            var template = JavaTemplate
                .builder("@Schema(" + buildArgString(remaining, newArgCode) + ")")
                .imports(SCHEMA_FQN)
                .javaParser(JavaParser.fromJavaVersion()
                    .classpathFromResources(ctx, "swagger-annotations-jakarta"))
                .build();

            return template.apply(getCursor(), original.getCoordinates().replace());
        }

        private String buildArgString(List<Expression> remaining, String newArgCode) {
            if (remaining.isEmpty()) {
                return newArgCode;
            }
            StringBuilder sb = new StringBuilder();
            for (Expression expr : remaining) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(expr.print(getCursor()).strip());
            }
            sb.append(", ").append(newArgCode);
            return sb.toString();
        }
    }
}
