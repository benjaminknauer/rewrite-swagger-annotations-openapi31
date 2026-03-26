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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * OpenRewrite recipe: migrates the OpenAPI 3.0 singular {@code type} attribute
 * in {@code @Schema} to the OpenAPI 3.1 compliant {@code types} array.
 *
 * <p>In OpenAPI 3.1 / JSON Schema 2020-12, the {@code type} keyword is an array.
 * swagger-annotations 2.2.x models this with the {@code types} attribute (string array),
 * while the legacy {@code type} attribute (single string) is kept for backwards
 * compatibility but should not be used in new code.</p>
 *
 * <p>Transformation rules:</p>
 * <ul>
 *   <li>{@code @Schema(type = "X")} → {@code @Schema(types = {"X"})}</li>
 *   <li>All other attributes (e.g. {@code description}, {@code minimum}) are preserved.</li>
 *   <li>If {@code types} (plural) is already present, the annotation is not changed (idempotent).</li>
 *   <li>If {@code nullable = true} is present, the annotation is skipped — use
 *       {@link NullableSchemaRecipe} or {@link SchemaToJSpecifyNullableRecipe} to handle
 *       that case, which will produce {@code types = {"X", "null"}}.</li>
 * </ul>
 *
 * <p>In {@link SpringdocOpenApi31Recipe} this recipe runs after the nullable migration,
 * so by the time it executes, all {@code type + nullable = true} combinations have
 * already been converted to {@code types = {"X", "null"}}.</p>
 */
class SchemaTypeToTypesArrayRecipe extends Recipe {

    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migrate @Schema(type=...) to OpenAPI 3.1 types array";
    }

    @Override
    public String getDescription() {
        return "Replaces the singular OpenAPI 3.0 attribute 'type = \"X\"' in @Schema annotations "
            + "with the OpenAPI 3.1 compliant array 'types = {\"X\"}'. "
            + "All other attributes are preserved. "
            + "Skipped when 'nullable = true' is present (use NullableSchemaRecipe for that case). "
            + "If a 'types' attribute is already present, the annotation is not changed (idempotent).";
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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SchemaTypeToTypesArrayVisitor();
    }

    private static class SchemaTypeToTypesArrayVisitor extends JavaIsoVisitor<ExecutionContext> {

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

            // Idempotency: skip if 'types' (plural) is already present
            boolean hasTypes = args.stream().anyMatch(arg ->
                arg instanceof J.Assignment a && "types".equals(extractKey(a))
            );
            if (hasTypes) {
                return visited;
            }

            // Skip if nullable=true is present — that combination is handled by the nullable recipes
            boolean hasNullableTrue = args.stream().anyMatch(arg ->
                arg instanceof J.Assignment a
                && "nullable".equals(extractKey(a))
                && a.getAssignment() instanceof J.Literal lit
                && Boolean.TRUE.equals(lit.getValue())
            );
            if (hasNullableTrue) {
                return visited;
            }

            Optional<String> typeValue = findStringArg(args, "type");
            if (typeValue.isEmpty()) {
                return visited;
            }

            // Replace 'type' with 'types = {"X"}' at the same position
            String escapedValue = typeValue.get().replace("\\", "\\\\").replace("\"", "\\\"");
            Expression typesArg = parseSingleArg(
                String.format("types = {\"%s\"}", escapedValue), ctx);

            return SchemaAnnotationUtil.transformArgs(
                visited,
                Set.of(),
                Map.of("type", typesArg),
                List.of()
            );
        }

        /** Parses a minimal draft annotation and returns its single argument as an LST node. */
        private Expression parseSingleArg(String argSrc, ExecutionContext ctx) {
            J.Annotation draft = JavaTemplate
                .builder("@Schema(" + argSrc + ")")
                .imports(SCHEMA_FQN)
                .javaParser(JavaParser.fromJavaVersion()
                    .classpathFromResources(ctx, "swagger-annotations-jakarta"))
                .build()
                .apply(getCursor(), getCursor().<J.Annotation>getValue().getCoordinates().replace());
            return draft.getArguments().get(0);
        }

        private boolean isSchemaAnnotation(J.Annotation annotation) {
            if (annotation.getType() != null) {
                return TypeUtils.isAssignableTo(SCHEMA_FQN, annotation.getType());
            }
            return "Schema".equals(annotation.getSimpleName());
        }

        private Optional<String> findStringArg(List<Expression> args, String key) {
            for (Expression arg : args) {
                if (arg instanceof J.Assignment a && key.equals(extractKey(a))) {
                    Expression value = a.getAssignment();
                    if (value instanceof J.Literal lit && lit.getValue() instanceof String s) {
                        return Optional.of(s);
                    }
                }
            }
            return Optional.empty();
        }

        private String extractKey(J.Assignment assignment) {
            return assignment.getVariable() instanceof J.Identifier id ? id.getSimpleName() : "";
        }
    }
}
