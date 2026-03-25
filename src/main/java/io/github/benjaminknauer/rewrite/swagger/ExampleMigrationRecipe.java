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

/**
 * OpenRewrite-Rezept: Migriert {@code @Schema(example = "foo")} von OpenAPI 3.0
 * auf die OpenAPI 3.1 / JSON-Schema-2020-12-konforme Syntax
 * {@code @Schema(examples = {"foo"})}.
 *
 * <p>Hintergrund: In OpenAPI 3.0 war {@code example} ein einzelner Wert am
 * Schema-Objekt. JSON Schema Draft 2020-12 (Basis von OA 3.1) definiert
 * {@code examples} als Array, sodass mehrere Beispielwerte angegeben werden
 * können. Das Singular-Feld {@code example} ist in OA 3.1 noch erlaubt,
 * aber {@code examples} ist die kanonische Form.</p>
 *
 * <p>Transformationsregeln:</p>
 * <ul>
 *   <li>{@code @Schema(example = "X")} → {@code @Schema(examples = {"X"})}</li>
 *   <li>Alle anderen Attribute (z.&nbsp;B. {@code description}, {@code type}) bleiben erhalten.</li>
 *   <li>Ist bereits {@code examples} vorhanden, wird die Annotation nicht verändert (idempotent).</li>
 * </ul>
 */
public class ExampleMigrationRecipe extends Recipe {

    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migriere @Schema(example=...) zu OpenAPI 3.1 examples-Array";
    }

    @Override
    public String getDescription() {
        return "Ersetzt das OpenAPI-3.0-Attribut 'example' in @Schema-Annotationen "
            + "durch das OpenAPI-3.1-konforme 'examples = {\"...\"}'-Array.";
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

            // Nicht transformieren wenn 'examples' (Plural) bereits vorhanden
            boolean hatExamples = args.stream().anyMatch(arg ->
                arg instanceof J.Assignment a && "examples".equals(extractKey(a))
            );
            if (hatExamples) {
                return visited;
            }

            Optional<String> exampleWert = findStringArg(args, "example");
            if (exampleWert.isEmpty()) {
                return visited;
            }

            // 'example' entfernen, alle anderen Attribute behalten
            List<Expression> verbleibend = new ArrayList<>();
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment && "example".equals(extractKey(assignment))) {
                    continue;
                }
                verbleibend.add(arg);
            }

            String escapedWert = exampleWert.get().replace("\\", "\\\\").replace("\"", "\\\"");
            String neuerArgCode = String.format("examples = {\"%s\"}", escapedWert);

            J.Annotation neueAnnotation = JavaTemplate
                .builder("@Schema(" + buildArgString(verbleibend, neuerArgCode) + ")")
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

        private String buildArgString(List<Expression> verbleibend, String neuerArgCode) {
            if (verbleibend.isEmpty()) {
                return neuerArgCode;
            }
            StringBuilder sb = new StringBuilder();
            for (Expression expr : verbleibend) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(expr.print(getCursor()).strip());
            }
            sb.append(", ").append(neuerArgCode);
            return sb.toString();
        }
    }
}
