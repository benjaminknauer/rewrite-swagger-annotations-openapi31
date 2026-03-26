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
import java.util.Set;

/**
 * OpenRewrite recipe: migrates {@code @Schema(example = "foo")} from OpenAPI 3.0
 * to the OpenAPI 3.1 / JSON Schema 2020-12 compliant syntax
 * {@code @Schema(examples = {"foo"})}.
 *
 * <p>In OpenAPI 3.0, {@code example} was a single value on the Schema object.
 * JSON Schema Draft 2020-12 (the basis of OA 3.1) defines {@code examples} as
 * an array. The singular {@code example} field is deprecated in OA 3.1 in
 * favour of {@code examples}.</p>
 *
 * <p><strong>Note:</strong> This recipe is enabled by default in
 * {@link SpringdocOpenApi31Recipe}. The migration changes the generated JSON
 * output ({@code "example": "foo"} → {@code "examples": ["foo"]}), which can
 * break tools, client generators, or documentation renderers that only look for
 * the singular field. Set {@code migrateExamples: false} to skip this migration
 * until all consumers of your API spec can handle the {@code examples} array
 * form.</p>
 *
 * <p>Transformation rules:</p>
 * <ul>
 *   <li>{@code @Schema(example = "X")} → {@code @Schema(examples = {"X"})}</li>
 *   <li>All other attributes (e.g. {@code description}, {@code type}) are preserved.</li>
 *   <li>If {@code examples} (plural) is already present, the annotation is not changed (idempotent).</li>
 * </ul>
 */
class ExampleMigrationRecipe extends Recipe {

    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migrate @Schema(example=...) to OpenAPI 3.1 examples array";
    }

    @Override
    public String getDescription() {
        return "Replaces the singular OpenAPI 3.0 attribute 'example = \"X\"' in @Schema annotations "
            + "with the OpenAPI 3.1 compliant array 'examples = {\"X\"}'. "
            + "All other attributes are preserved. "
            + "If an 'examples' attribute is already present, the annotation is not changed (idempotent).";
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
        return new ExampleMigrationVisitor();
    }

    private static class ExampleMigrationVisitor extends JavaIsoVisitor<ExecutionContext> {

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

            // Do not transform if 'examples' (plural) is already present
            boolean hasExamples = args.stream().anyMatch(arg ->
                arg instanceof J.Assignment a && "examples".equals(extractKey(a))
            );
            if (hasExamples) {
                return visited;
            }

            Optional<String> exampleValue = findStringArg(args, "example");
            if (exampleValue.isEmpty()) {
                return visited;
            }

            // Remove 'example', keep all other attributes
            List<Expression> remaining = new ArrayList<>();
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment && "example".equals(extractKey(assignment))) {
                    continue;
                }
                remaining.add(arg);
            }

            String escapedValue = exampleValue.get().replace("\\", "\\\\").replace("\"", "\\\"");
            String newArgCode = String.format("examples = {\"%s\"}", escapedValue);

            J.Annotation newAnnotation = JavaTemplate
                .builder("@Schema(" + buildArgString(remaining, newArgCode) + ")")
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

        private Optional<String> findStringArg(List<Expression> args, String key) {
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment && key.equals(extractKey(assignment))) {
                    Expression value = assignment.getAssignment();
                    if (value instanceof J.Literal literal && literal.getValue() instanceof String s) {
                        return Optional.of(s);
                    }
                }
            }
            return Optional.empty();
        }

        private String extractKey(J.Assignment assignment) {
            Expression variable = assignment.getVariable();
            return variable instanceof J.Identifier id ? id.getSimpleName() : "";
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
