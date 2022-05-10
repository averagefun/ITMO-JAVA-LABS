package gui.addition;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class FilterListener implements DocumentListener {
    private final Runnable runnable;

    public FilterListener(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        runnable.run();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        runnable.run();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        runnable.run();
    }
}
