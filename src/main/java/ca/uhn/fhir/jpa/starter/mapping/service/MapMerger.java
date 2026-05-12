package ca.uhn.fhir.jpa.starter.mapping.service;

import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StructureMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MapMerger {

	/**
	 * Merges imported StructureMap into base StructureMap.
	 *
	 * Merge paradigm:
	 * - imported map gives the main ordering skeleton;
	 * - matching local/base elements patch imported elements at imported positions;
	 * - local/base-only elements are reinserted between their closest matched anchors;
	 * - same strategy is applied recursively to groups, rules, targets and dependents.
	 */
	public static void mergeStructureMaps(StructureMap base, StructureMap imported) {
		mergeVariables(base.getContained(), imported.getContained());

		base.setGroup(
			mergeGroupLists(
				new ArrayList<>(base.getGroup()),
				imported.getGroup()
			)
		);

		if (!base.hasDescription() && imported.hasDescription()) {
			base.setDescription(imported.getDescription());
		}
	}

	private static List<StructureMap.StructureMapGroupComponent> mergeGroupLists(
		List<StructureMap.StructureMapGroupComponent> baseGroups,
		List<StructureMap.StructureMapGroupComponent> importedGroups
	) {
		return mergeListsPreservingImportedOrderWithLocalInsertions(
			baseGroups,
			importedGroups,
			MapMerger::sameGroupSignature,
			StructureMap.StructureMapGroupComponent::copy,
			MapMerger::mergeGroup
		);
	}

	static StructureMap.StructureMapGroupComponent mergeGroup(
		StructureMap.StructureMapGroupComponent baseGroup,
		StructureMap.StructureMapGroupComponent importedGroup
	) {
		StructureMap.StructureMapGroupComponent mergedGroup = baseGroup.copy();

		if (!mergedGroup.hasTypeMode() && importedGroup.hasTypeMode()) {
			mergedGroup.setTypeMode(importedGroup.getTypeMode());
		}

		if (!mergedGroup.hasDocumentation() && importedGroup.hasDocumentation()) {
			mergedGroup.setDocumentation(importedGroup.getDocumentation());
		}

		if (mergedGroup.getInput().isEmpty() && !importedGroup.getInput().isEmpty()) {
			importedGroup.getInput().forEach(input -> mergedGroup.getInput().add(input.copy()));
		}

		mergedGroup.setRule(
			mergeRuleLists(
				new ArrayList<>(mergedGroup.getRule()),
				importedGroup.getRule()
			)
		);

		return mergedGroup;
	}

	private static List<StructureMap.StructureMapGroupRuleComponent> mergeRuleLists(
		List<StructureMap.StructureMapGroupRuleComponent> baseRules,
		List<StructureMap.StructureMapGroupRuleComponent> importedRules
	) {
		return mergeListsPreservingImportedOrderWithLocalInsertions(
			baseRules,
			importedRules,
			MapMerger::sameRuleSignature,
			StructureMap.StructureMapGroupRuleComponent::copy,
			MapMerger::mergeRule
		);
	}

	static StructureMap.StructureMapGroupRuleComponent mergeRule(
		StructureMap.StructureMapGroupRuleComponent baseRule,
		StructureMap.StructureMapGroupRuleComponent importedRule
	) {
		StructureMap.StructureMapGroupRuleComponent mergedRule = baseRule.copy();

		if (mergedRule.getSource().isEmpty() && !importedRule.getSource().isEmpty()) {
			importedRule.getSource().forEach(source -> mergedRule.getSource().add(source.copy()));
		}

		mergedRule.setTarget(
			mergeTargetLists(
				new ArrayList<>(mergedRule.getTarget()),
				importedRule.getTarget()
			)
		);

		mergedRule.setRule(
			mergeRuleLists(
				new ArrayList<>(mergedRule.getRule()),
				importedRule.getRule()
			)
		);

		mergedRule.setDependent(
			mergeDependentLists(
				new ArrayList<>(mergedRule.getDependent()),
				importedRule.getDependent()
			)
		);

		if (!mergedRule.hasDocumentation() && importedRule.hasDocumentation()) {
			mergedRule.setDocumentation(importedRule.getDocumentation());
		}

		return mergedRule;
	}

	private static List<StructureMap.StructureMapGroupRuleTargetComponent> mergeTargetLists(
		List<StructureMap.StructureMapGroupRuleTargetComponent> baseTargets,
		List<StructureMap.StructureMapGroupRuleTargetComponent> importedTargets
	) {
		return mergeListsPreservingImportedOrderWithLocalInsertions(
			baseTargets,
			importedTargets,
			MapMerger::sameTargetSignature,
			StructureMap.StructureMapGroupRuleTargetComponent::copy,

			// Local/base target overrides imported target content,
			// but it is placed at the imported target position.
			(baseTarget, importedTarget) -> baseTarget.copy()
		);
	}

	private static List<StructureMap.StructureMapGroupRuleDependentComponent> mergeDependentLists(
		List<StructureMap.StructureMapGroupRuleDependentComponent> baseDependents,
		List<StructureMap.StructureMapGroupRuleDependentComponent> importedDependents
	) {
		return mergeListsPreservingImportedOrderWithLocalInsertions(
			baseDependents,
			importedDependents,
			MapMerger::sameDependentSignature,
			StructureMap.StructureMapGroupRuleDependentComponent::copy,

			// Local/base dependent overrides imported dependent content,
			// but it is placed at the imported dependent position.
			(baseDependent, importedDependent) -> baseDependent.copy()
		);
	}

	static void mergeVariables(List<Resource> baseContained, List<Resource> importedContained) {
		if (importedContained == null || importedContained.isEmpty()) {
			return;
		}

		for (Resource importedResource : importedContained) {
			boolean alreadyExists = baseContained.stream()
				.anyMatch(existing -> existing.getIdElement() != null
					&& importedResource.getIdElement() != null
					&& Objects.equals(
					existing.getIdElement().getIdPart(),
					importedResource.getIdElement().getIdPart()
				));

			if (!alreadyExists) {
				baseContained.add(importedResource.copy());
			}
		}
	}

	/**
	 * Generic "woven merge".
	 *
	 * importedItems define the primary order.
	 *
	 * For matching items:
	 * - base item patches imported item;
	 * - merged item is emitted at the imported item position.
	 *
	 * For base-only items:
	 * - they are reinserted before the next matched imported anchor,
	 *   preserving their original local/base order.
	 *
	 * For imported-only items:
	 * - they are emitted in imported order.
	 */
	private static <T> List<T> mergeListsPreservingImportedOrderWithLocalInsertions(
		List<T> baseItems,
		List<T> importedItems,
		SameSignature<T> sameSignature,
		CopyFn<T> copyFn,
		MergeFn<T> mergeFn
	) {
		List<MatchPair> matches = computeOrderedMatches(baseItems, importedItems, sameSignature);

		// Important:
		// If there is no override at all in this list, imported elements are the safe skeleton.
		// Local additions come after, because they may depend on imported-created variables.
		if (matches.isEmpty()) {
			List<T> result = new ArrayList<>();

			for (T importedItem : importedItems) {
				result.add(copyFn.copy(importedItem));
			}

			for (T baseItem : baseItems) {
				result.add(copyFn.copy(baseItem));
			}

			return result;
		}

		List<T> result = new ArrayList<>();
		int baseCursor = 0;
		int matchCursor = 0;

		for (int importedIndex = 0; importedIndex < importedItems.size(); importedIndex++) {
			T importedItem = importedItems.get(importedIndex);

			boolean isMatchedImported =
				matchCursor < matches.size()
					&& matches.get(matchCursor).importedIndex() == importedIndex;

			if (isMatchedImported) {
				MatchPair match = matches.get(matchCursor);
				int matchedBaseIndex = match.baseIndex();

				while (baseCursor < matchedBaseIndex) {
					result.add(copyFn.copy(baseItems.get(baseCursor)));
					baseCursor++;
				}

				T baseItem = baseItems.get(matchedBaseIndex);
				result.add(mergeFn.merge(baseItem, importedItem));

				baseCursor = matchedBaseIndex + 1;
				matchCursor++;
			} else {
				result.add(copyFn.copy(importedItem));
			}
		}

		while (baseCursor < baseItems.size()) {
			result.add(copyFn.copy(baseItems.get(baseCursor)));
			baseCursor++;
		}

		return result;
	}

	/**
	 * Computes ordered matches using LCS.
	 *
	 * This is more robust than a simple first-match lookup because StructureMaps
	 * may contain repeated rule names in some places.
	 */
	private static <T> List<MatchPair> computeOrderedMatches(
		List<T> baseItems,
		List<T> importedItems,
		SameSignature<T> sameSignature
	) {
		int baseSize = baseItems.size();
		int importedSize = importedItems.size();

		int[][] dp = new int[baseSize + 1][importedSize + 1];

		for (int i = baseSize - 1; i >= 0; i--) {
			for (int j = importedSize - 1; j >= 0; j--) {
				if (sameSignature.test(baseItems.get(i), importedItems.get(j))) {
					dp[i][j] = 1 + dp[i + 1][j + 1];
				} else {
					dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
				}
			}
		}

		List<MatchPair> matches = new ArrayList<>();

		int i = 0;
		int j = 0;

		while (i < baseSize && j < importedSize) {
			if (sameSignature.test(baseItems.get(i), importedItems.get(j))) {
				matches.add(new MatchPair(i, j));
				i++;
				j++;
			} else if (dp[i + 1][j] >= dp[i][j + 1]) {
				i++;
			} else {
				j++;
			}
		}

		return matches;
	}

	static boolean sameGroupSignature(
		StructureMap.StructureMapGroupComponent g1,
		StructureMap.StructureMapGroupComponent g2
	) {
		if (!Objects.equals(g1.getName(), g2.getName())) {
			return false;
		}

		if (g1.getInput().size() != g2.getInput().size()) {
			return false;
		}

		for (int i = 0; i < g1.getInput().size(); i++) {
			StructureMap.StructureMapGroupInputComponent left = g1.getInput().get(i);
			StructureMap.StructureMapGroupInputComponent right = g2.getInput().get(i);

			if (!Objects.equals(left.getName(), right.getName())) {
				return false;
			}

			if (!Objects.equals(left.getType(), right.getType())) {
				return false;
			}

			if (!Objects.equals(left.getMode(), right.getMode())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Rule matching is intentionally based on name only.
	 *
	 * This matches the current override convention: a child/local SM overrides
	 * a parent/imported rule by using the same rule name and rule hierarchy.
	 *
	 * If duplicated sibling names remain an issue, this can later be strengthened
	 * with source context/element/condition, but that would require minimal child
	 * maps to preserve exact source signatures.
	 */
	private static boolean sameRuleSignature(
		StructureMap.StructureMapGroupRuleComponent r1,
		StructureMap.StructureMapGroupRuleComponent r2
	) {
		return Objects.equals(r1.getName(), r2.getName());
	}

	/**
	 * Target matching.
	 *
	 * The goal is to replace the same target path/variable at the imported
	 * position while allowing transform/parameters to be overridden locally.
	 */
	private static boolean sameTargetSignature(
		StructureMap.StructureMapGroupRuleTargetComponent t1,
		StructureMap.StructureMapGroupRuleTargetComponent t2
	) {
		return Objects.equals(t1.getContext(), t2.getContext())
			&& Objects.equals(t1.getContextType(), t2.getContextType())
			&& Objects.equals(t1.getElement(), t2.getElement())
			&& Objects.equals(t1.getVariable(), t2.getVariable());
	}

	private static boolean sameDependentSignature(
		StructureMap.StructureMapGroupRuleDependentComponent d1,
		StructureMap.StructureMapGroupRuleDependentComponent d2
	) {
		if (!Objects.equals(d1.getName(), d2.getName())) {
			return false;
		}
		return true;
	}

	@FunctionalInterface
	private interface SameSignature<T> {
		boolean test(T left, T right);
	}

	@FunctionalInterface
	private interface CopyFn<T> {
		T copy(T item);
	}

	@FunctionalInterface
	private interface MergeFn<T> {
		T merge(T baseItem, T importedItem);
	}

	private record MatchPair(int baseIndex, int importedIndex) {
	}
}