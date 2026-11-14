package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.ui.*;

/**
 * A placement rule: how often it searches (cell size, chance). What it looks for, what must hold and what it builds are
 * nodes, edited in the Graph view.
 */
final class RuleEditor extends Editor {

    RuleEditor(StudioApp app, Asset a) { super(app, a); }

    @Override
    protected void build() {
        section("Identity");
        field("Label", str("name", "", "Rule label"), "Shown in lists.");

        section("How often", "the rule searches the world on a grid");
        field("Cell size", num("cell", 96, 16, 4096, 8, true, "Rule cell size"),
                "The world is cut into squares this wide and each is searched at most once. Bigger = rarer.");
        field("Chance", num("chance", 0.6, 0, 1, 0.05, false, "Rule chance"), "Chance that a square is searched at all.");

        section("What it does", "built as nodes");
        note("Open the Graph view. Find: how a spot is found. Conditions: what must hold there. Place: what to build.", Ui.ACCENT);
        note("Place actions can use expressions, e.g. choose the tree whose height is at least the log's length: prefab.height >= log.length", Ui.TEXT_FAINT);
        note("Addons can add new finders, conditions and actions; they appear in the node palette automatically.", Ui.TEXT_FAINT);
    }
}
