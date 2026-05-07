package ca.uhn.fhir.jpa.starter.mapping.service;

import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StructureMap;

import java.util.*;

public class MapMerger {

	/**
	 * Merges imported StructureMap into the base one.
	 * Local definitions override imported ones.
	 *
	 * Merge strategy:
	 * - Imported map order is used as the reference order.
	 * - If a local group/rule with the same signature/name exists, it is merged at the imported position.
	 * - Local-only groups/rules are appended at the end.
	 */
	public static void mergeStructureMaps(StructureMap base, StructureMap imported) {
		mergeVariables(base.getContained(), imported.getContained());

		List<StructureMap.StructureMapGroupComponent> mergedGroups = new ArrayList<>();
		Set<StructureMap.StructureMapGroupComponent> consumedBaseGroups = new HashSet<>();

		for (StructureMap.StructureMapGroupComponent importedGroup : imported.getGroup()) {
			Optional<StructureMap.StructureMapGroupComponent> existingGroupOpt = base.getGroup().stream()
				.filter(g -> sameGroupSignature(g, importedGroup))
				.findFirst();

			if (existingGroupOpt.isPresent()) {
				StructureMap.StructureMapGroupComponent baseGroup = existingGroupOpt.get();
				StructureMap.StructureMapGroupComponent mergedGroup = baseGroup.copy();

				mergeGroupsPreservingImportedOrder(mergedGroup, importedGroup);

				mergedGroups.add(mergedGroup);
				consumedBaseGroups.add(baseGroup);
			} else {
				mergedGroups.add(importedGroup.copy());
			}
		}

		// Append local-only groups at the end.
		for (StructureMap.StructureMapGroupComponent baseGroup : base.getGroup()) {
			if (!consumedBaseGroups.contains(baseGroup)) {
				mergedGroups.add(baseGroup.copy());
			}
		}

		base.setGroup(mergedGroups);

		if (!base.hasDescription() && imported.hasDescription()) {
			base.setDescription(imported.getDescription());
		}
	}

	/**
	 * Merge contained resources from imported maps into the base one.
	 * Locally defined resources have priority.
	 */
	private static void mergeVariables(List<Resource> baseContained, List<Resource> importedContained) {
		if (importedContained == null || importedContained.isEmpty()) {
			return;
		}
		if (baseContained == null) {
			baseContained = new ArrayList<>();
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
	 * Compares groups by name and input/output types (signature).
	 */
	private static boolean sameGroupSignature(
		StructureMap.StructureMapGroupComponent g1,
		StructureMap.StructureMapGroupComponent g2
	) {
		if (!Objects.equals(g1.getName(), g2.getName())) return false;
		if (g1.getInput().size() != g2.getInput().size()) return false;

		for (int i = 0; i < g1.getInput().size(); i++) {
			if (!Objects.equals(g1.getInput().get(i).getType(), g2.getInput().get(i).getType())) {
				return false;
			}
			if (!Objects.equals(g1.getInput().get(i).getMode(), g2.getInput().get(i).getMode())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Merges rules of two groups.
	 *
	 * Important:
	 * The imported group order is preserved.
	 * Local rules with the same name override/complete imported rules at the imported rule position.
	 * Local-only rules are appended at the end.
	 */
	private static void mergeGroupsPreservingImportedOrder(
		StructureMap.StructureMapGroupComponent baseGroup,
		StructureMap.StructureMapGroupComponent importedGroup
	) {
		List<StructureMap.StructureMapGroupRuleComponent> localRules = new ArrayList<>(baseGroup.getRule());
		List<StructureMap.StructureMapGroupRuleComponent> mergedRules = mergeRuleListsPreservingImportedOrder(
			localRules,
			importedGroup.getRule()
		);

		baseGroup.setRule(mergedRules);

		// Keep local inputs/metadata as priority.
		// If you later want imported inputs to complete missing local inputs,
		// that can be added explicitly here.
	}

	/**
	 * Merge a list of local rules with imported rules while preserving imported order.
	 *
	 * Algorithm:
	 * - iterate imported rules in their original order;
	 * - if a local rule with the same name exists, merge it with the imported rule and place it here;
	 * - otherwise copy the imported rule;
	 * - append local-only rules at the end, preserving their local order.
	 */
	private static List<StructureMap.StructureMapGroupRuleComponent> mergeRuleListsPreservingImportedOrder(
		List<StructureMap.StructureMapGroupRuleComponent> localRules,
		List<StructureMap.StructureMapGroupRuleComponent> importedRules
	) {
		List<StructureMap.StructureMapGroupRuleComponent> mergedRules = new ArrayList<>();
		Set<StructureMap.StructureMapGroupRuleComponent> consumedLocalRules = new HashSet<>();

		for (StructureMap.StructureMapGroupRuleComponent importedRule : importedRules) {
			Optional<StructureMap.StructureMapGroupRuleComponent> localRuleOpt = localRules.stream()
				.filter(localRule -> Objects.equals(localRule.getName(), importedRule.getName()))
				.findFirst();

			if (localRuleOpt.isPresent()) {
				StructureMap.StructureMapGroupRuleComponent localRule = localRuleOpt.get();

				StructureMap.StructureMapGroupRuleComponent mergedRule = localRule.copy();
				mergeRulesPreservingImportedOrder(mergedRule, importedRule);

				mergedRules.add(mergedRule);
				consumedLocalRules.add(localRule);
			} else {
				mergedRules.add(importedRule.copy());
			}
		}

		for (StructureMap.StructureMapGroupRuleComponent localRule : localRules) {
			if (!consumedLocalRules.contains(localRule)) {
				mergedRules.add(localRule.copy());
			}
		}

		return mergedRules;
	}

	/**
	 * Merges two rules recursively.
	 *
	 * Local rule has priority for source and target:
	 * - if the local rule has sources, keep them;
	 * - otherwise copy imported sources;
	 * - if the local rule has targets, keep them;
	 * - otherwise copy imported targets.
	 *
	 * Subrules are merged using imported order as reference.
	 */
	private static void mergeRulesPreservingImportedOrder(
		StructureMap.StructureMapGroupRuleComponent localRule,
		StructureMap.StructureMapGroupRuleComponent importedRule
	) {
		if (localRule.getSource().isEmpty() && !importedRule.getSource().isEmpty()) {
			localRule.getSource().addAll(
				importedRule.getSource().stream()
					.map(StructureMap.StructureMapGroupRuleSourceComponent::copy)
					.toList()
			);
		}

		mergeTargets(localRule, importedRule);

		List<StructureMap.StructureMapGroupRuleComponent> mergedSubRules =
			mergeRuleListsPreservingImportedOrder(
				new ArrayList<>(localRule.getRule()),
				importedRule.getRule()
			);

		localRule.setRule(mergedSubRules);

		mergeDependents(localRule, importedRule);
	}

	private static void mergeTargets(
		StructureMap.StructureMapGroupRuleComponent localRule,
		StructureMap.StructureMapGroupRuleComponent importedRule
	) {
		List<StructureMap.StructureMapGroupRuleTargetComponent> localTargets =
			new ArrayList<>(localRule.getTarget());

		List<StructureMap.StructureMapGroupRuleTargetComponent> mergedTargets =
			mergeTargetListsPreservingImportedOrder(localTargets, importedRule.getTarget());

		localRule.setTarget(mergedTargets);
	}

	/**
	 * Merge targets while preserving imported target order.
	 *
	 * Strategy:
	 * - imported targets define the reference order;
	 * - if a local target matches an imported target, merge/replace it at the imported position;
	 * - if no local target matches, keep the imported target;
	 * - append local-only targets at the end.
	 */
	private static List<StructureMap.StructureMapGroupRuleTargetComponent> mergeTargetListsPreservingImportedOrder(
		List<StructureMap.StructureMapGroupRuleTargetComponent> localTargets,
		List<StructureMap.StructureMapGroupRuleTargetComponent> importedTargets
	) {
		List<StructureMap.StructureMapGroupRuleTargetComponent> mergedTargets = new ArrayList<>();
		Set<StructureMap.StructureMapGroupRuleTargetComponent> consumedLocalTargets = new HashSet<>();

		for (StructureMap.StructureMapGroupRuleTargetComponent importedTarget : importedTargets) {
			Optional<StructureMap.StructureMapGroupRuleTargetComponent> localTargetOpt = localTargets.stream()
				.filter(localTarget -> sameTargetSignature(localTarget, importedTarget))
				.findFirst();

			if (localTargetOpt.isPresent()) {
				StructureMap.StructureMapGroupRuleTargetComponent localTarget = localTargetOpt.get();

				// Local target has priority for the actual mapping definition.
				mergedTargets.add(localTarget.copy());
				consumedLocalTargets.add(localTarget);
			} else {
				mergedTargets.add(importedTarget.copy());
			}
		}

		for (StructureMap.StructureMapGroupRuleTargetComponent localTarget : localTargets) {
			if (!consumedLocalTargets.contains(localTarget)) {
				mergedTargets.add(localTarget.copy());
			}
		}

		return mergedTargets;
	}

	/**
	 * Defines when a local target overrides an imported target.
	 *
	 * This intentionally ignores transform and parameters so that a local target can
	 * override the value/transform of the same target path.
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

	private static void mergeDependents(
		StructureMap.StructureMapGroupRuleComponent localRule,
		StructureMap.StructureMapGroupRuleComponent importedRule
	) {
		for (StructureMap.StructureMapGroupRuleDependentComponent importedDependent : importedRule.getDependent()) {
			boolean exists = localRule.getDependent().stream()
				.anyMatch(dep -> Objects.equals(dep.getName(), importedDependent.getName()));

			if (!exists) {
				localRule.getDependent().add(importedDependent.copy());
			}
		}
	}
}
