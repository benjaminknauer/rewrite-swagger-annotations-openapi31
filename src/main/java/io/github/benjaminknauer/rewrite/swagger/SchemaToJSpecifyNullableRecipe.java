package io.github.benjaminknauer.rewrite.swagger;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.maven.AddDependency;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenRewrite recipe: migrates {@code @Schema(nullable = true)} to either
 * {@code @org.jspecify.annotations.Nullable} or the OA 3.1 types-array form,
 * depending on whether the annotation carries an explicit {@code type} attribute.
 *
 * <p><strong>Transformation rules:</strong></p>
 * <ul>
 *   <li>{@code @Schema(type = "X", nullable = true)} →
 *       {@code @Schema(types = {"X", "null"})} — the explicit type is preserved
 *       in the annotation; no {@code @Nullable} is added.</li>
 *   <li>{@code @Schema(type = "X", nullable = true, description = "...")} →
 *       {@code @Schema(types = {"X", "null"}, description = "...")}</li>
 *   <li>{@code @Schema(nullable = true)} (no explicit {@code type}) →
 *       {@code @Nullable} — the entire {@code @Schema} is removed.</li>
 *   <li>{@code @Schema(nullable = true, description = "...")} →
 *       {@code @Nullable @Schema(description = "...")} — only {@code nullable} stripped.</li>
 *   <li>If {@code @Nullable} is already present alongside a no-type {@code @Schema},
 *       the field is not changed (idempotent).</li>
 *   <li>Only fields and method parameters ({@code J.VariableDeclarations}) are handled.</li>
 * </ul>
 *
 * <p>This recipe uses a two-phase approach via {@link ScanningRecipe}:</p>
 * <ol>
 *   <li><strong>Scan phase</strong>: scans all Java sources to detect whether any
 *       {@code @Schema(nullable = true)} annotation without an explicit {@code type}
 *       attribute exists in the project.</li>
 *   <li><strong>Visit phase</strong>: only if the scan found at least one candidate,
 *       the transformation is applied. This ensures that
 *       {@code org.jspecify:jspecify} is only added to {@code pom.xml} when
 *       {@code @Nullable} is actually introduced.</li>
 * </ol>
 *
 * <p><strong>Note:</strong> This recipe and {@link NullableSchemaRecipe} are mutually exclusive —
 * do not combine them. Choose one migration strategy and apply it consistently.</p>
 */
class SchemaToJSpecifyNullableRecipe extends ScanningRecipe<AtomicBoolean> {

    private static final String SCHEMA_FQN   = "io.swagger.v3.oas.annotations.media.Schema";
    private static final String NULLABLE_FQN = "org.jspecify.annotations.Nullable";

    @Override
    public String getDisplayName() {
        return "Migrate @Schema(nullable=true) — @Nullable (JSpecify) or types array";
    }

    @Override
    public String getDescription() {
        return "Migrates '@Schema(nullable = true)' in two ways depending on context: "
            + "if an explicit 'type' attribute is present, converts to the OpenAPI 3.1 "
            + "'types = {\"X\", \"null\"}' form (same as NullableSchemaRecipe). "
            + "If no explicit 'type' is set, replaces with '@org.jspecify.annotations.Nullable' "
            + "and removes 'nullable' from @Schema (or the entire @Schema if it has no other "
            + "attributes) — letting springdoc-openapi infer the types array at runtime. "
            + "Uses a scan-first approach: org.jspecify:jspecify is only added to pom.xml when "
            + "@Nullable is actually needed (i.e. the project contains @Schema(nullable=true) "
            + "without an explicit 'type' attribute). "
            + "Idempotent: fields already annotated with @Nullable are not changed. "
            + "This recipe is an alternative to NullableSchemaRecipe — do not use both.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("openapi", "swagger", "springdoc", "migration", "jspecify");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    // -------------------------------------------------------------------------
    // ScanningRecipe API
    // -------------------------------------------------------------------------

    /**
     * Initial accumulator: {@code false} — no candidate found yet.
     */
    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    /**
     * Scanner: detects whether any {@code @Schema(nullable = true)} annotation
     * (with or without an explicit {@code type} attribute) exists in the project.
     * Sets the accumulator to {@code true} on first match.
     *
     * <p>Both cases require transformation:
     * <ul>
     *   <li>With {@code type}: converted to {@code types = {"T", "null"}} array — no jSpecify.</li>
     *   <li>Without {@code type}: replaced with {@code @Nullable} — jSpecify dependency added.</li>
     * </ul>
     * The jSpecify dependency is only added when {@code @Nullable} is actually present in the
     * transformed code, guaranteed by {@link AddDependency}'s {@code onlyIfUsing} guard.</p>
     */
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean hasCandidate) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                if (!hasCandidate.get()
                        && isSchemaAnnotation(annotation)
                        && hasNullableTrue(annotation)) {
                    hasCandidate.set(true);
                }
                return annotation;
            }
        };
    }

    /**
     * Adds {@code org.jspecify:jspecify} to the project's {@code pom.xml} only when
     * the scan detected at least one candidate — i.e. only when {@code @Nullable}
     * will actually be introduced.
     * {@link AddDependency} is idempotent and uses {@code onlyIfUsing} as an
     * additional guard.
     */
    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
            new AddDependency(
                "org.jspecify", "jspecify", "1.0.0",
                null,            // versionPattern
                "compile",       // scope
                null,            // releasesOnly
                null,            // type
                NULLABLE_FQN,    // onlyIfUsing — dep added only if @Nullable is present
                null,            // classifier
                null,            // optional
                null,            // familyPattern
                null             // acceptTransitive
            )
        );
    }

    /**
     * Visitor: only active when the scan phase detected at least one candidate.
     * If no {@code @Schema(nullable = true)} without {@code type} was found,
     * returns a no-op visitor — no files are modified and no jspecify dependency
     * is introduced.
     */
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean hasCandidate) {
        if (!hasCandidate.get()) {
            return TreeVisitor.noop();
        }
        return new SchemaToJSpecifyVisitor();
    }

    // -------------------------------------------------------------------------

    private static class SchemaToJSpecifyVisitor extends JavaIsoVisitor<ExecutionContext> {

        /**
         * Handles the <em>explicit-type case</em>:
         * {@code @Schema(type = "X", nullable = true, ...)} →
         * {@code @Schema(types = {"X", "null"}, ...)}.
         *
         * <p>This runs as part of the normal tree traversal inside
         * {@link #visitVariableDeclarations}. After this visitor finishes,
         * any remaining {@code @Schema(nullable = true)} annotations in the field
         * are guaranteed to have <em>no</em> explicit {@code type} attribute,
         * and will be handled by the variable-declarations logic below.</p>
         */
        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation visited = super.visitAnnotation(annotation, ctx);

            if (!isSchemaAnnotation(visited)) return visited;
            if (!hasNullableTrue(visited)) return visited;

            Optional<String> explicitType = findStringArg(visited.getArguments(), "type");
            if (explicitType.isEmpty()) return visited; // handled in visitVariableDeclarations

            // Keep all args except 'nullable' and 'type'; prepend new types={...}
            List<Expression> remaining = stripArgs(visited.getArguments(), "nullable", "type");
            String typesCode = String.format("types = {\"%s\", \"null\"}", explicitType.get());
            String argString = buildArgString(remaining, typesCode);

            return JavaTemplate
                .builder("@Schema(" + argString + ")")
                .imports(SCHEMA_FQN)
                .javaParser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta"))
                .build()
                .apply(getCursor(), visited.getCoordinates().replace());
        }

        /**
         * Handles the <em>no-explicit-type case</em>:
         * {@code @Schema(nullable = true)} (possibly with other attributes but no {@code type})
         * → {@code @Nullable} + stripped / removed {@code @Schema}.
         *
         * <p>Called after {@link #visitAnnotation} has already converted any explicit-type
         * schemas to the types-array form, so only genuinely no-type cases remain.</p>
         */
        @Override
        public J.VariableDeclarations visitVariableDeclarations(
                J.VariableDeclarations varDecls, ExecutionContext ctx) {

            J.VariableDeclarations visited = super.visitVariableDeclarations(varDecls, ctx);

            // Find a @Schema without explicit type= that still carries nullable=true
            // (explicit-type cases were already converted by visitAnnotation above)
            Optional<J.Annotation> schemaOpt = visited.getLeadingAnnotations().stream()
                .filter(ann -> isSchemaAnnotation(ann)
                            && hasNullableTrue(ann)
                            && findStringArg(ann.getArguments(), "type").isEmpty())
                .findFirst();

            if (schemaOpt.isEmpty()) return visited;

            // Idempotency: skip if @Nullable is already present
            if (visited.getLeadingAnnotations().stream().anyMatch(this::isNullableAnnotation)) {
                return visited;
            }

            J.Annotation schema = schemaOpt.get();
            List<Expression> remainingArgs = stripArgs(schema.getArguments(), "nullable");

            // Step 1: handle the @Schema annotation
            J.VariableDeclarations withCleanedSchema;
            if (remainingArgs.isEmpty()) {
                // @Schema had only nullable=true → remove the entire annotation
                withCleanedSchema = visited.withLeadingAnnotations(
                    visited.getLeadingAnnotations().stream()
                        .filter(ann -> ann != schema)
                        .toList()
                );
                maybeRemoveImport(SCHEMA_FQN);
            } else {
                // @Schema has other attributes → strip nullable=true via a follow-up visitor
                doAfterVisit(new StripNullableFromSchemaVisitor());
                withCleanedSchema = visited;
            }

            // Step 2: add @Nullable
            maybeAddImport(NULLABLE_FQN);
            return JavaTemplate
                .builder("@Nullable")
                .imports(NULLABLE_FQN)
                .javaParser(JavaParser.fromJavaVersion().classpath("jspecify"))
                .build()
                .apply(
                    updateCursor(withCleanedSchema),
                    withCleanedSchema.getCoordinates().addAnnotation(
                        Comparator.comparing(J.Annotation::getSimpleName)
                    )
                );
        }

        // --- helpers ---------------------------------------------------------

        private boolean isSchemaAnnotation(J.Annotation annotation) {
            if (annotation.getType() == null) {
                return "Schema".equals(annotation.getSimpleName());
            }
            return TypeUtils.isAssignableTo(SCHEMA_FQN, annotation.getType());
        }

        private boolean hasNullableTrue(J.Annotation annotation) {
            List<Expression> args = annotation.getArguments();
            if (args == null) return false;
            return args.stream().anyMatch(arg ->
                arg instanceof J.Assignment a
                && "nullable".equals(extractKey(a))
                && a.getAssignment() instanceof J.Literal lit
                && Boolean.TRUE.equals(lit.getValue())
            );
        }

        private boolean isNullableAnnotation(J.Annotation annotation) {
            if (annotation.getType() != null) {
                return TypeUtils.isAssignableTo(NULLABLE_FQN, annotation.getType());
            }
            return "Nullable".equals(annotation.getSimpleName());
        }

        private Optional<String> findStringArg(List<Expression> args, String key) {
            if (args == null) return Optional.empty();
            return args.stream()
                .filter(arg -> arg instanceof J.Assignment a && key.equals(extractKey(a)))
                .map(arg -> ((J.Assignment) arg).getAssignment())
                .filter(val -> val instanceof J.Literal lit && lit.getValue() instanceof String)
                .map(val -> (String) ((J.Literal) val).getValue())
                .findFirst();
        }

        private List<Expression> stripArgs(List<Expression> args, String... keysToRemove) {
            if (args == null) return List.of();
            Set<String> keys = Set.of(keysToRemove);
            return args.stream()
                .filter(arg -> !(arg instanceof J.Assignment a && keys.contains(extractKey(a))))
                .toList();
        }

        private String extractKey(J.Assignment assignment) {
            return assignment.getVariable() instanceof J.Identifier id ? id.getSimpleName() : "";
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

    // -------------------------------------------------------------------------

    /**
     * Follow-up visitor queued via {@code doAfterVisit}: strips {@code nullable = true}
     * from {@code @Schema} annotations that have no explicit {@code type} but still carry
     * other attributes (e.g. {@code description}).
     * The fully-empty case is handled in the main visitor via {@code withLeadingAnnotations}.
     */
    private static class StripNullableFromSchemaVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation visited = super.visitAnnotation(annotation, ctx);

            if (!isSchemaAnnotation(visited)) return visited;

            List<Expression> args = visited.getArguments();
            if (args == null || args.isEmpty()) return visited;

            boolean hasNullableTrue = args.stream().anyMatch(arg ->
                arg instanceof J.Assignment a
                && "nullable".equals(extractKey(a))
                && a.getAssignment() instanceof J.Literal lit
                && Boolean.TRUE.equals(lit.getValue())
            );
            if (!hasNullableTrue) return visited;

            List<Expression> remaining = args.stream()
                .filter(arg -> !(arg instanceof J.Assignment a && "nullable".equals(extractKey(a))))
                .toList();

            if (remaining.isEmpty()) {
                // Already removed by the main visitor; nothing to do here
                return visited;
            }

            // Rebuild @Schema without nullable=true via JavaTemplate for correct formatting
            StringBuilder sb = new StringBuilder();
            for (Expression expr : remaining) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(expr.print(getCursor()).strip());
            }
            return JavaTemplate
                .builder("@Schema(" + sb + ")")
                .imports(SCHEMA_FQN)
                .javaParser(JavaParser.fromJavaVersion().classpath("swagger-annotations-jakarta"))
                .build()
                .apply(getCursor(), visited.getCoordinates().replace());
        }

        private boolean isSchemaAnnotation(J.Annotation annotation) {
            if (annotation.getType() == null) {
                return "Schema".equals(annotation.getSimpleName());
            }
            return TypeUtils.isAssignableTo(SCHEMA_FQN, annotation.getType());
        }

        private String extractKey(J.Assignment assignment) {
            return assignment.getVariable() instanceof J.Identifier id ? id.getSimpleName() : "";
        }
    }

    // -------------------------------------------------------------------------
    // Static helper methods reused by the scanner and the main visitor
    // -------------------------------------------------------------------------

    private static boolean isSchemaAnnotation(J.Annotation annotation) {
        if (annotation.getType() == null) {
            return "Schema".equals(annotation.getSimpleName());
        }
        return TypeUtils.isAssignableTo(SCHEMA_FQN, annotation.getType());
    }

    private static boolean hasNullableTrue(J.Annotation annotation) {
        List<Expression> args = annotation.getArguments();
        if (args == null) return false;
        return args.stream().anyMatch(arg ->
            arg instanceof J.Assignment a
            && "nullable".equals(extractKey(a))
            && a.getAssignment() instanceof J.Literal lit
            && Boolean.TRUE.equals(lit.getValue())
        );
    }

    private static Optional<String> findStringArg(List<Expression> args, String key) {
        if (args == null) return Optional.empty();
        return args.stream()
            .filter(arg -> arg instanceof J.Assignment a && key.equals(extractKey(a)))
            .map(arg -> ((J.Assignment) arg).getAssignment())
            .filter(val -> val instanceof J.Literal lit && lit.getValue() instanceof String)
            .map(val -> (String) ((J.Literal) val).getValue())
            .findFirst();
    }

    private static String extractKey(J.Assignment assignment) {
        return assignment.getVariable() instanceof J.Identifier id ? id.getSimpleName() : "";
    }
}
