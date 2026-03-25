package com.benjaminknauer.rewrite.swagger;

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
 * OpenRewrite-Rezept: Migriert {@code @Schema(nullable = true)} von OpenAPI 3.0
 * auf die OpenAPI 3.1 konforme Syntax {@code @Schema(types = {"T", "null"})}.
 *
 * <p>Hintergrund: In OpenAPI 3.1 wurde {@code nullable} zugunsten von JSON Schema
 * Draft 2020-12 entfernt. Null-Typen werden jetzt als Array im {@code type}-Feld
 * ausgedrückt, z.&nbsp;B. {@code ["string", "null"]}.</p>
 *
 * <p>Transformationsregeln:</p>
 * <ul>
 *   <li>{@code @Schema(nullable = true)} → {@code @Schema(types = {"string", "null"})}</li>
 *   <li>{@code @Schema(type = "X", nullable = true)} → {@code @Schema(types = {"X", "null"})}</li>
 *   <li>Alle anderen Attribute (z.&nbsp;B. {@code description}) bleiben erhalten.</li>
 *   <li>{@code nullable = false} wird ignoriert (keine Änderung).</li>
 * </ul>
 */
public class NullableSchemaRecipe extends Recipe {

    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migriere @Schema(nullable=true) zu OpenAPI 3.1 types-Array";
    }

    @Override
    public String getDescription() {
        return "Ersetzt das OpenAPI-3.0-Attribut 'nullable = true' in @Schema-Annotationen "
            + "durch das OpenAPI-3.1-konforme 'types = {\"T\", \"null\"}'-Array.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new NullableSchemaVisitor();
    }

    private static class NullableSchemaVisitor extends JavaIsoVisitor<ExecutionContext> {

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

            String baseType = findStringArg(args, "type").orElse("string");

            // Alle args außer 'nullable' und 'type' behalten
            List<Expression> verbleibend = new ArrayList<>();
            for (Expression arg : args) {
                if (arg instanceof J.Assignment assignment) {
                    String key = extractKey(assignment);
                    if ("nullable".equals(key) || "type".equals(key)) {
                        continue;
                    }
                }
                verbleibend.add(arg);
            }

            // Neues types-Attribut als String-Array-Literal bauen
            String typesCode = String.format("types = {\"%s\", \"null\"}", baseType);
            J.Annotation neueAnnotation = buildAnnotationWithArgs(visited, verbleibend, typesCode);

            return neueAnnotation;
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
            List<Expression> verbleibend,
            String neuerArgCode
        ) {
            var template = JavaTemplate
                .builder("@Schema(" + buildArgString(verbleibend, neuerArgCode) + ")")
                .imports(SCHEMA_FQN)
                .javaParser(JavaParser.fromJavaVersion()
                    .classpath("swagger-annotations-jakarta"))
                .build();

            return template.apply(getCursor(), original.getCoordinates().replace());
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
