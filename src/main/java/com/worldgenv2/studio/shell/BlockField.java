package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.ui.*;

import java.util.function.Consumer;

/** A block id field: the block's icon, the id as editable text, and a Pick button that opens the searchable block list. */
final class BlockField extends Widget {
    private final TextField field;
    private final Button pick;
    private final Consumer<String> onChange;

    BlockField(TextField field, Consumer<String> onChange) {
        this.field = add(field);
        this.onChange = onChange;
        this.pick = add(new Button("Pick", this::open));
        pick.tooltip = "Search every block, with icons";
        this.h = Ui.FIELD_H;
    }

    private void open() {
        if (root == null) return;
        BlockPicker.open(root, x, y + h + 2, "Pick a block", id -> { field.setText(id); onChange.accept(id); });
    }

    @Override
    public void layout() {
        field.setBounds(x + 22, y, w - 22 - 40 - Ui.GAP, h);
        pick.setBounds(x + w - 40, y, 40, h);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        layout();
        g.box(x, y, 20, h, Ui.BG_INPUT, Ui.LINE_STRONG);
        BlockCatalog.Entry e = BlockCatalog.of(field.text());
        if (e != null) g.item(e.stack(), x + 2, y + (h - 16) / 2, 16);
        else g.textCentered("?", x, y, 20, h, Ui.TEXT_FAINT);
    }
}
