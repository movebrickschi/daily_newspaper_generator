package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 带历史 author 候选弹出的 {@link JBTextField}。
 *
 * <p>支持逗号分隔多 author 输入；每输入一个新 token 时基于已知 author 列表（懒加载）
 * 显示前缀匹配的候选；上下键选择，Enter 接收。</p>
 *
 * <p>候选源通过 {@link #setCandidateSupplier(Supplier)} 注入，避免在 UI 线程上做 git 调用。</p>
 *
 * @author Liu Chunchi
 */
public class AuthorAutoCompleteField extends JBTextField {

    private Supplier<Collection<String>> candidateSupplier = List::of;
    private List<String> cachedCandidates = new ArrayList<>();
    private boolean cacheLoaded = false;

    @Nullable
    private JBPopup currentPopup;

    public AuthorAutoCompleteField() {
        setColumns(20);
        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { triggerSuggestions(); }
            @Override public void removeUpdate(DocumentEvent e) { triggerSuggestions(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ac.cancel");
        getActionMap().put("ac.cancel", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                hidePopup();
            }
        });
    }

    public void setCandidateSupplier(@NotNull Supplier<Collection<String>> supplier) {
        this.candidateSupplier = supplier;
        this.cacheLoaded = false;
    }

    /** 直接灌入候选列表（异步加载完成后回填）。 */
    public void setCandidates(@NotNull Collection<String> candidates) {
        Set<String> unique = new LinkedHashSet<>();
        for (String s : candidates) {
            if (s != null && !s.isBlank()) {
                unique.add(s.trim());
            }
        }
        cachedCandidates = new ArrayList<>(unique);
        cacheLoaded = true;
    }

    private void ensureCache() {
        if (cacheLoaded) return;
        cacheLoaded = true;
        try {
            Collection<String> raw = candidateSupplier.get();
            Set<String> unique = new LinkedHashSet<>();
            if (raw != null) {
                for (String s : raw) {
                    if (s != null && !s.isBlank()) {
                        unique.add(s.trim());
                    }
                }
            }
            cachedCandidates = new ArrayList<>(unique);
        } catch (Exception e) {
            cachedCandidates = new ArrayList<>();
        }
    }

    private void triggerSuggestions() {
        SwingUtilities.invokeLater(() -> {
            ensureCache();
            String typed = getText() == null ? "" : getText();
            int caret = getCaretPosition();
            int tokenStart = lastDelimiter(typed, caret) + 1;
            String prefix = typed.substring(tokenStart, Math.min(caret, typed.length())).trim();
            if (prefix.isEmpty()) {
                hidePopup();
                return;
            }
            List<String> matches = new ArrayList<>();
            String lower = prefix.toLowerCase(java.util.Locale.ROOT);
            for (String c : cachedCandidates) {
                if (c.toLowerCase(java.util.Locale.ROOT).contains(lower)) {
                    matches.add(c);
                }
                if (matches.size() >= 8) break;
            }
            if (matches.isEmpty()) {
                hidePopup();
                return;
            }
            showPopup(matches, tokenStart, caret);
        });
    }

    private int lastDelimiter(@NotNull String s, int caret) {
        int max = Math.min(caret, s.length());
        for (int i = max - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ',' || c == ' ') return i;
        }
        return -1;
    }

    private void showPopup(@NotNull List<String> items, int tokenStart, int caret) {
        hidePopup();
        DefaultListModel<String> model = new DefaultListModel<>();
        items.forEach(model::addElement);
        JBList<String> list = new JBList<>(model);
        list.setSelectedIndex(0);
        JBScrollPane scroll = new JBScrollPane(list);
        // 弹层宽度跟随当前输入框宽度（最小 240px, 最大 480px），避免长 email 被截断
        int popupWidth = Math.max(JBUI.scale(240), Math.min(JBUI.scale(480), getWidth()));
        int popupHeight = JBUI.scale(Math.min(160, 24 * items.size() + 8));
        scroll.setPreferredSize(new Dimension(popupWidth, popupHeight));

        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(scroll, list)
                .setFocusable(false)
                .setRequestFocus(false)
                .setMovable(false)
                .setResizable(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .addListener(new JBPopupListener() {
                    @Override
                    public void onClosed(@NotNull LightweightWindowEvent event) {
                        if (currentPopup == event.asPopup()) {
                            currentPopup = null;
                        }
                    }
                })
                .createPopup();
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                String selected = list.getSelectedValue();
                if (selected != null) {
                    applySelection(selected, tokenStart, caret);
                    popup.cancel();
                }
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "ac.down");
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "ac.up");
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ac.accept");
        getActionMap().put("ac.down", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int idx = list.getSelectedIndex();
                if (idx < model.size() - 1) list.setSelectedIndex(idx + 1);
            }
        });
        getActionMap().put("ac.up", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int idx = list.getSelectedIndex();
                if (idx > 0) list.setSelectedIndex(idx - 1);
            }
        });
        getActionMap().put("ac.accept", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                String selected = list.getSelectedValue();
                if (selected != null) {
                    applySelection(selected, tokenStart, caret);
                    popup.cancel();
                }
            }
        });
        currentPopup = popup;
        popup.showUnderneathOf(this);
    }

    private void applySelection(@NotNull String value, int tokenStart, int caret) {
        String original = getText();
        if (original == null) original = "";
        String left = original.substring(0, tokenStart);
        String right = original.substring(Math.min(caret, original.length()));
        String inserted;
        if (!left.isEmpty() && left.charAt(left.length() - 1) == ',') {
            inserted = " " + value;
        } else if (!left.isEmpty() && left.charAt(left.length() - 1) != ' ') {
            inserted = value;
        } else {
            inserted = value;
        }
        String updated = left + inserted + right;
        setText(updated);
        setCaretPosition(Math.min(updated.length(), left.length() + inserted.length()));
    }

    private void hidePopup() {
        if (currentPopup != null && !currentPopup.isDisposed()) {
            currentPopup.cancel();
        }
        currentPopup = null;
        JComponent root = this;
        root.getInputMap().remove(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
        root.getInputMap().remove(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
        root.getInputMap().remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
    }
}
