package io.github.benjaminknauer.rewrite.swagger;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared utility for transforming {@code @Schema} annotation arguments while
 * preserving multiline formatting (newlines and indentation between arguments).
 *
 * <p>The standard approach of rebuilding the whole annotation via
 * {@code JavaTemplate.builder("@Schema(" + argString + ")")} discards the
 * {@link JRightPadded} prefixes of each argument, collapsing multiline
 * annotations onto a single line. This utility operates directly on the
 * annotation's {@link JContainer} so that untouched arguments and their
 * surrounding whitespace are kept exactly as they are.</p>
 */
class SchemaAnnotationUtil {

    private SchemaAnnotationUtil() {}

    /**
     * Transforms a {@code @Schema} annotation's argument list while preserving
     * multiline formatting.
     *
     * <ul>
     *   <li>Arguments whose key is in {@code keysToDelete} are removed.</li>
     *   <li>Arguments whose key appears in {@code replacements} are replaced by
     *       the mapped expression. The replacement inherits the original
     *       argument's prefix (whitespace / indentation).</li>
     *   <li>Arguments in {@code newArgsAtEnd} are appended after all remaining
     *       arguments, using the last seen argument prefix for consistent
     *       indentation. The trailing whitespace before {@code )} is preserved.</li>
     *   <li>All other arguments and their {@link JRightPadded} padding are
     *       kept exactly as-is.</li>
     * </ul>
     *
     * @param original     the annotation to transform
     * @param keysToDelete argument keys to remove without replacement
     * @param replacements argument keys to replace with a new expression
     * @param newArgsAtEnd additional arguments to append at the end
     * @return the transformed annotation, or {@code original} if no change occurred
     */
    static J.Annotation transformArgs(
        J.Annotation original,
        Set<String> keysToDelete,
        Map<String, Expression> replacements,
        List<Expression> newArgsAtEnd
    ) {
        JContainer<Expression> container = original.getPadding().getArguments();
        if (container == null) {
            return original;
        }

        List<JRightPadded<Expression>> elements = container.getPadding().getElements();
        List<JRightPadded<Expression>> result = new ArrayList<>();
        Space lastSeenPrefix = Space.EMPTY;

        for (JRightPadded<Expression> paddedExpr : elements) {
            String key = argKey(paddedExpr.getElement());
            lastSeenPrefix = paddedExpr.getElement().getPrefix();

            if (keysToDelete.contains(key)) {
                // Delete: skip this element
            } else if (replacements.containsKey(key)) {
                // Replace at same position, preserving whitespace prefix
                Expression newExpr = replacements.get(key).withPrefix(lastSeenPrefix);
                result.add(paddedExpr.withElement(newExpr));
            } else {
                // Keep unchanged
                result.add(paddedExpr);
            }
        }

        // Append new arguments at the end, preserving trailing whitespace before ')'
        for (int i = 0; i < newArgsAtEnd.size(); i++) {
            Expression newArg = newArgsAtEnd.get(i).withPrefix(lastSeenPrefix);
            boolean isLastAppended = (i == newArgsAtEnd.size() - 1);

            if (isLastAppended && !result.isEmpty()) {
                // Transfer the trailing 'after' (space before ')') to the new last element
                JRightPadded<Expression> currentLast = result.get(result.size() - 1);
                Space trailingAfter = currentLast.getAfter();
                result.set(result.size() - 1, currentLast.withAfter(Space.EMPTY));
                result.add(JRightPadded.build(newArg).withAfter(trailingAfter));
            } else {
                result.add(JRightPadded.build(newArg).withAfter(Space.EMPTY));
            }
        }

        if (result.isEmpty()) {
            return original;
        }

        // Normalize the prefix of the first remaining element: if the original first
        // element was deleted, the new first element may have a "after-comma" prefix (e.g. " ")
        // that would produce "@Schema( description = ...)" with a spurious leading space.
        if (!elements.isEmpty()) {
            Space firstOrigPrefix = elements.get(0).getElement().getPrefix();
            JRightPadded<Expression> firstResult = result.get(0);
            result.set(0, firstResult.withElement(
                firstResult.getElement().withPrefix(firstOrigPrefix)));
        }

        return original.getPadding().withArguments(
            container.getPadding().withElements(result)
        );
    }

    /** Extracts the simple name of an annotation argument assignment. */
    static String argKey(Expression expr) {
        if (expr instanceof J.Assignment a && a.getVariable() instanceof J.Identifier id) {
            return id.getSimpleName();
        }
        return "";
    }
}
