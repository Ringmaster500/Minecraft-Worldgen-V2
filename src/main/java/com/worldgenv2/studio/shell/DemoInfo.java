package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.graph.NodeSchemas;
import com.worldgenv2.studio.ui.Widget;

import java.util.List;

/** What the Guide needs from a node demo: the list of stand-in inputs it filled in, to print under the page. */
interface DemoInfo {
    List<String> standIns();

    /** The demo for a node, or null when the node has none (or the engine cannot run it on its own). */
    static Widget forNode(NodeSchemas.NodeType t) {
        Widget d = DensityDemo.forNode(t);
        if (d == null) d = SceneDemo.forNode(t);
        return d;
    }
}
