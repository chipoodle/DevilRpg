package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.PlayerScrollableSkills;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import net.minecraftforge.fml.loading.toposort.TopologicalSort;

public class ScrollableSkillLoadFix {
	private static final Logger LOGGER = LogManager.getLogger();
    private static Map<SkillElement, List<SkillElement>> roots;

    public static void loadVisibility(final PlayerScrollableSkills playerAdvancements, final Set<SkillElement> visible, final Set<SkillElement> visibilityChanged, final Map<SkillElement, SkillProgress> progress, final Set<SkillElement> progressChanged, final Predicate<SkillElement> shouldBeVisible) {
        LOGGER.info("Using new advancement loading for {}", playerAdvancements);
        if (roots == null) throw new RuntimeException("Why did the advancements not load yet?!");
        final Set<SkillElement> set = new HashSet<>();
        for(Map.Entry<SkillElement, SkillProgress> entry : progress.entrySet()) {
            if (entry.getValue().isDone()) {
                set.add(entry.getKey());
                progressChanged.add(entry.getKey());
            }
        }

        roots.values().stream().flatMap(Collection::stream).filter(adv -> containsAncestor(adv, set)).forEach(adv->updateVisibility(adv, visible, visibilityChanged, progress, progressChanged, shouldBeVisible));
    }

    private static boolean containsAncestor(final SkillElement adv, final Set<SkillElement> set) {
        return set.contains(adv) || (adv.getParent() != null && containsAncestor(adv.getParent(), set));
    }

    private static void updateVisibility(final SkillElement adv, final Set<SkillElement> visible, final Set<SkillElement> visibilityChanged, final Map<SkillElement, SkillProgress> progress, final Set<SkillElement> progressChanged, Predicate<SkillElement> shouldBeVisible) {
        boolean flag = shouldBeVisible.test(adv);
        boolean flag1 = visible.contains(adv);
        if (flag && !flag1) {
            visible.add(adv);
            visibilityChanged.add(adv);
            if (progress.containsKey(adv)) {
                progressChanged.add(adv);
            }
        } else if (!flag && flag1) {
            visible.remove(adv);
            visibilityChanged.add(adv);
        }
        if (flag!=flag1 && adv.getParent()!=null)
            updateVisibility(adv.getParent(), visible, visibilityChanged, progress, progressChanged, shouldBeVisible);
    }

    public static void buildSortedTrees(final Set<SkillElement> roots) {
    	ScrollableSkillLoadFix.roots = roots.stream()
                .map(ScrollableSkillLoadFix::buildGraph)
                .map(g -> TopologicalSort.topologicalSort(g, Comparator.comparing(SkillElement::getId)))
                .collect(Collectors.toMap(lst -> lst.get(0), Function.identity()));
    }

    private static Graph<SkillElement> buildGraph(final SkillElement root) {
        final MutableGraph<SkillElement> tree = GraphBuilder.directed().build();
        addEdgesAndChildren(root, tree);
        return tree;
    }

    private static void addEdgesAndChildren(final SkillElement root, final MutableGraph<SkillElement> tree) {
        tree.addNode(root);
        for (SkillElement adv: root.getChildren()) {
            addEdgesAndChildren(adv, tree);
            tree.putEdge(root, adv);
        }
    }
}
