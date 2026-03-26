package io.github.benjaminknauer.rewrite.swagger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

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
 *       {@code @Schema(nullable = true)} annotation exists in the project.</li>
 *   <li><strong>Visit phase</strong>: applies the transformation only if the scan found
 *       at least one candidate. {@code @Nullable} is introduced only for annotations
 *       without an explicit {@code type} attribute.</li>
 * </ol>
 *
 * <p>{@code org.jspecify:jspecify} is added to {@code pom.xml} via {@link AddDependency}
 * with {@code onlyIfUsing = "org.jspecify.annotations.Nullable"}. Because sub-recipes in
 * {@code getRecipeList()} run in a subsequent cycle <em>after</em> the visit phase has
 * completed, {@link AddDependency} scans the already-transformed source and therefore
 * only adds jspecify when {@code @Nullable} was actually introduced.</p>
 *
 * <p><strong>Note:</strong> This recipe and {@link NullableSchemaRecipe} are mutually exclusive —
 * do not combine them. Choose one migration strategy and apply it consistently.</p>
 */
class SchemaToJSpecifyNullableRecipe extends ScanningRecipe<AtomicBoolean> {

    private static final String SCHEMA_FQN   = "io.swagger.v3.oas.annotations.media.Schema";
    private static final String NULLABLE_FQN = "org.jspecify.annotations.Nullable";

    @Option(
        displayName = "Add org.jspecify:jspecify to pom.xml",
        description = "When true (default), adds 'org.jspecify:jspecify' as a compile dependency "
            + "to pom.xml if @Nullable is introduced and jspecify is not yet declared. "
            + "Set to false when jspecify already arrives as a transitive dependency and "
            + "you do not want it listed explicitly in your POM.",
        example = "false",
        required = false
    )
    @Nullable
    private final Boolean addJSpecifyDependency;

    /** Default: all options enabled. */
    SchemaToJSpecifyNullableRecipe() {
        this(null);
    }

    @JsonCreator
    SchemaToJSpecifyNullableRecipe(
        @JsonProperty("addJSpecifyDependency") @Nullable Boolean addJSpecifyDependency
    ) {
        this.addJSpecifyDependency = addJSpecifyDependency;
    }

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
     * Scanner: detects whether any {@code @Schema(nullable = true)} annotation exists.
     * Sets the accumulator to {@code true} on first match.
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
     * Returns {@link AddDependency} as a sub-recipe. Sub-recipes in {@code getRecipeList()}
     * run in a subsequent cycle, <em>after</em> the visit phase has transformed the Java
     * sources. {@link AddDependency} therefore scans the already-modified files and uses
     * its {@code onlyIfUsing} guard to add {@code org.jspecify:jspecify} only when
     * {@code @org.jspecify.annotations.Nullable} is actually present.
     *
     * <p><strong>Constructor parameter order for {@link AddDependency}:</strong>
     * groupId, artifactId, version, versionPattern, scope, releasesOnly,
     * <strong>onlyIfUsing</strong> (pos 7), type (pos 8), classifier, optional,
     * familyPattern, acceptTransitive.</p>
     */
    @Override
    public List<Recipe> getRecipeList() {
        if (Boolean.FALSE.equals(addJSpecifyDependency)) {
            return List.of();
        }
        return List.of(
            new AddDependency(
                "org.jspecify", "jspecify", "1.0.0",
                null,            // versionPattern
                "compile",       // scope
                null,            // releasesOnly
                NULLABLE_FQN,    // onlyIfUsing (position 7) — only add when @Nullable is used
                null,            // type (position 8) — Maven packaging type, null = default (jar)
                null,            // classifier
                null,            // optional
                null,            // familyPattern
                null             // acceptTransitive
            )
        );
    }

    /**
     * Visitor: only active when the scan phase detected at least one candidate.
     * If no {@code @Schema(nullable = true)} was found, returns a no-op visitor.
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
         * <p>After this visitor runs, any remaining {@code @Schema(nullable = true)} in
         * the field are guaranteed to have <em>no</em> explicit {@code type} attribute,
         * and will be handled by {@link #visitVariableDeclarations}.</p>
         */
        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation visited = super.visitAnnotation(annotation, ctx);

            if (!isSchemaAnnotation(visited)) return visited;
            if (!hasNullableTrue(visited)) return visited;

            Optional<String> explicitType = findStringArg(visited.getArguments(), "type");
            if (explicitType.isEmpty()) return visited; // handled in visitVariableDeclarations

            // Replace 'type' with 'types = {"X", "null"}', delete 'nullable'
            String escapedType = explicitType.get().replace("\\", "\\\\").replace("\"", "\\\"");
            Expression typesArg = parseSingleArg(
                String.format("types = {\"%s\", \"null\"}", escapedType), ctx);

            return SchemaAnnotationUtil.transformArgs(
                visited, Set.of("nullable"), Map.of("type", typesArg), List.of());
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

        /**
         * Handles the <em>no-explicit-type case</em>:
         * {@code @Schema(nullable = true)} →
         * {@code @Nullable} + stripped / removed {@code @Schema}.
         *
         * <p>Called after {@link #visitAnnotation} has already converted any explicit-type
         * schemas to the types-array form, so only genuinely no-type cases remain.</p>
         */
        @Override
        public J.VariableDeclarations visitVariableDeclarations(
                J.VariableDeclarations varDecls, ExecutionContext ctx) {

            J.VariableDeclarations visited = super.visitVariableDeclarations(varDecls, ctx);

            // Find a @Schema without explicit type= that still carries nullable=true
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
                .javaParser(JavaParser.fromJavaVersion()
                    .classpathFromResources(ctx, "jspecify"))
                .build()
                .apply(
                    updateCursor(withCleanedSchema),
                    withCleanedSchema.getCoordinates().addAnnotation(
                        Comparator.comparing(J.Annotation::getSimpleName)
                    )
                );
        }

        // --- helpers ---

        private boolean isNullableAnnotation(J.Annotation annotation) {
            if (annotation.getType() != null) {
                return TypeUtils.isAssignableTo(NULLABLE_FQN, annotation.getType());
            }
            return "Nullable".equals(annotation.getSimpleName());
        }

        private List<Expression> stripArgs(List<Expression> args, String... keysToRemove) {
            if (args == null) return List.of();
            Set<String> keys = Set.of(keysToRemove);
            return args.stream()
                .filter(arg -> !(arg instanceof J.Assignment a && keys.contains(extractKey(a))))
                .toList();
        }

    }

    // -------------------------------------------------------------------------

    /**
     * Follow-up visitor queued via {@code doAfterVisit}: strips {@code nullable = true}
     * from {@code @Schema} annotations that have other attributes remaining.
     */
    private static class StripNullableFromSchemaVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation visited = super.visitAnnotation(annotation, ctx);

            if (!isSchemaAnnotation(visited)) return visited;

            List<Expression> args = visited.getArguments();
            if (args == null || args.isEmpty()) return visited;

            if (!hasNullableTrue(visited)) return visited;

            List<Expression> remaining = args.stream()
                .filter(arg -> !(arg instanceof J.Assignment a && "nullable".equals(extractKey(a))))
                .toList();

            if (remaining.isEmpty()) {
                return visited;
            }

            return SchemaAnnotationUtil.transformArgs(
                visited, Set.of("nullable"), Map.of(), List.of());
        }
    }

    // -------------------------------------------------------------------------
    // Static helper methods
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
