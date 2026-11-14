package com.worldgenv2.studio.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Centered dialog: message, optional text input, optional dropdown, OK / Cancel. */
public class Modal extends UiRoot.Overlay {

    private final String title;
    private final List<String> lines = new ArrayList<>();
    private TextField input;
    private Dropdown choice;
    private final Button ok, cancel;
    private Consumer<String> onOk;
    private final int panelW;
    private int panelH, px, py;
    private String error = "";
    private java.util.function.Function<String, String> validator;

    private Modal(String title, int panelW, String okLabel, Button.Style okStyle) {
        this.title = title;
        this.panelW = panelW;
        this.modal = true;
        ok = add(new Button(okLabel, this::accept).style(okStyle));
        cancel = add(new Button("Cancel", this::dismiss));
    }

    public static Modal prompt(UiRoot root, String title, String message, String initial, String okLabel,
                               java.util.function.Function<String, String> validator, Consumer<String> onOk) {
        Modal m = new Modal(title, 460, okLabel, Button.Style.PRIMARY);
        if (message != null) m.lines.add(message);
        m.onOk = onOk;
        m.validator = validator;
        m.input = m.add(new TextField().filter(TextField.Filter.TEXT));
        m.input.setText(initial);
        m.input.onEnter = s -> m.accept();
        m.input.onChange = s -> m.error = "";
        root.showOverlay(m);
        m.input.focus();
        m.input.setText(initial);
        return m;
    }

    public static Modal choose(UiRoot root, String title, String message, List<String> options, String selected,
                               String okLabel, Consumer<String> onOk) {
        Modal m = new Modal(title, 460, okLabel, Button.Style.PRIMARY);
        if (message != null) m.lines.add(message);
        m.onOk = onOk;
        m.choice = m.add(new Dropdown().options(options).selected(selected));
        root.showOverlay(m);
        return m;
    }

    public static Modal confirm(UiRoot root, String title, String message, String okLabel, boolean danger, Runnable onYes) {
        Modal m = new Modal(title, 460, okLabel, danger ? Button.Style.DANGER : Button.Style.PRIMARY);
        for (String l : message.split("\n")) m.lines.add(l);
        m.onOk = s -> onYes.run();
        root.showOverlay(m);
        return m;
    }

    private void accept() {
        String val = input != null ? input.text() : choice != null ? choice.selected() : "";
        if (validator != null) {
            String err = validator.apply(val);
            if (err != null) { error = err; return; }
        }
        dismiss();
        if (onOk != null) onOk.accept(val);
    }

    @Override
    public void dismiss() {
        if (root != null) root.closeOverlay(this);
    }

    @Override
    public void layout() {
        int rows = lines.size();
        panelH = 30 + rows * 12 + (input != null || choice != null ? 26 : 4) + (error.isEmpty() ? 0 : 0) + 34;
        px = (w - panelW) / 2;
        py = (h - panelH) / 2 - 20;
        int cy = py + 30 + rows * 12 + 4;
        if (input != null) input.setBounds(px + 12, cy, panelW - 24, Ui.FIELD_H);
        if (choice != null) choice.setBounds(px + 12, cy, panelW - 24, Ui.FIELD_H);
        int by = py + panelH - 26;
        ok.setBounds(px + panelW - 12 - 70, by, 70, 18);
        cancel.setBounds(px + panelW - 12 - 70 - 6 - 70, by, 70, 18);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        layout();
        g.fill(0, 0, w, h, 0x99000000);
        g.fill(px + 3, py + 3, panelW, panelH, 0x66000000);
        g.box(px, py, panelW, panelH, Ui.BG_RAISED, Ui.LINE_STRONG);
        g.fill(px, py, panelW, 2, Ui.ACCENT);
        g.textMid(title, px + 12, py + 8, 14, Ui.TEXT);
        int ty = py + 28;
        for (String l : lines) { g.text(g.fit(l, panelW - 24), px + 12, ty, Ui.TEXT_DIM); ty += 12; }
        if (!error.isEmpty()) g.text(g.fit(error, panelW - 24), px + 12, py + panelH - 38, Ui.RED);
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (key == Ui.KEY_ENTER || key == Ui.KEY_KP_ENTER) { accept(); return true; }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        return true; // swallow clicks on the dim backdrop
    }
}
