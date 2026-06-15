package com.terminalide.ui;

import com.terminalide.editor.TextBuffer;
import com.terminalide.file.FileManager;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MainWindow {
    private final String initialPath;
    private Terminal terminal;
    private Screen screen;
    private TextGraphics textGraphics;

    private FileManager fileManager;
    private FileBrowser fileBrowser;
    private EditorPanel editorPanel;
    private StatusBar statusBar;

    private final List<TextBuffer> tabs;
    private int activeTab;

    private enum Focus { BROWSER, EDITOR }
    private Focus focus;

    private boolean running;
    private int termWidth;
    private int termHeight;

    private static final int BROWSER_WIDTH = 30;
    private static final int MIN_EDITOR_WIDTH = 20;

    public MainWindow(String initialPath) {
        this.initialPath = initialPath;
        this.tabs = new ArrayList<>();
        this.activeTab = 0;
        this.focus = Focus.EDITOR;
        this.running = false;
    }

    public void run() {
        try {
            initTerminal();
            running = true;
            mainLoop();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void initTerminal() throws IOException {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        factory.setPreferTerminalEmulator(true);
        terminal = factory.createTerminal();
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null); // Hide cursor, we draw our own

        fileManager = new FileManager();
        fileBrowser = new FileBrowser(fileManager, initialPath);
        editorPanel = new EditorPanel();
        statusBar = new StatusBar();

        // Start with a new empty tab
        addNewTab("untitled");

        TerminalSize size = screen.getTerminalSize();
        termWidth = size.getColumns();
        termHeight = size.getRows();
    }

    private void mainLoop() throws IOException {
        while (running) {
            TerminalSize newSize = screen.doResizeIfNecessary();
            if (newSize != null) {
                termWidth = newSize.getColumns();
                termHeight = newSize.getRows();
            }

            textGraphics = screen.newTextGraphics();
            updateDrawing();
            screen.refresh();

            KeyStroke key = terminal.pollInput();
            if (key != null) {
                handleGlobalInput(key);
            } else {
                // Small sleep to avoid busy-waiting
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void updateDrawing() {
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);

        // Clear screen
        for (int y = 0; y < termHeight; y++) {
            for (int x = 0; x < termWidth; x++) {
                textGraphics.setCharacter(x, y, ' ');
            }
        }

        int browserW = Math.min(BROWSER_WIDTH, termWidth - MIN_EDITOR_WIDTH);
        int editorX = browserW;
        int editorW = termWidth - browserW;
        int statusY = termHeight - 1;
        int mainHeight = termHeight - 1;

        // Draw file browser
        fileBrowser.draw(textGraphics, 0, 0, browserW, mainHeight, focus == Focus.BROWSER);

        // Draw editor tab bar + content
        drawTabBar(editorX, 0, editorW);
        if (!tabs.isEmpty()) {
            TextBuffer buffer = tabs.get(activeTab);
            String title = buffer.getFilePath() != null
                    ? Path.of(buffer.getFilePath()).getFileName().toString()
                    : "untitled";
            if (buffer.isModified()) title += " *";
            editorPanel.draw(textGraphics, editorX, 1, editorW, mainHeight - 1, buffer, title, focus == Focus.EDITOR);
        }

        // Draw status bar
        if (!tabs.isEmpty()) {
            TextBuffer buffer = tabs.get(activeTab);
            String fileName = buffer.getFilePath() != null
                    ? Path.of(buffer.getFilePath()).getFileName().toString()
                    : "untitled";
            String mode = focus == Focus.EDITOR ? "EDIT" : "BROWSE";
            String lineInfo = String.format("%d:%d", buffer.getCursorRow() + 1, buffer.getCursorCol() + 1);
            statusBar.draw(textGraphics, statusY, termWidth, mode, fileName, buffer.isModified(), lineInfo);
        } else {
            statusBar.draw(textGraphics, statusY, termWidth, "EXIT", "", false, "");
        }
    }

    private void drawTabBar(int x, int y, int width) {
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);

        if (tabs.isEmpty()) return;

        int tabX = x + 1;
        for (int i = 0; i < tabs.size(); i++) {
            TextBuffer tb = tabs.get(i);
            String name = tb.getFilePath() != null
                    ? Path.of(tb.getFilePath()).getFileName().toString()
                    : "untitled";
            if (tb.isModified()) name = "[" + name + "]";
            String label = " " + (i + 1) + ": " + name + " ";

            if (i == activeTab) {
                textGraphics.setBackgroundColor(TextColor.ANSI.BLUE);
                textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
            } else {
                textGraphics.setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT);
                textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
            }

            if (tabX + label.length() < x + width) {
                textGraphics.putString(tabX, y, label);
                tabX += label.length();
            }

            textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        }
        // Fill rest of tab bar
        for (int tx = tabX; tx < x + width; tx++) {
            textGraphics.setCharacter(tx, y, ' ');
        }
    }

    private void handleGlobalInput(KeyStroke key) {
        // Always handle Ctrl+Q to quit
        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            char c = key.getCharacter();
            if (c == 'q' && key.isCtrlDown()) {
                running = false;
                return;
            }
            if (c == 's' && key.isCtrlDown()) {
                saveCurrentFile();
                return;
            }
            if (c == 'o' && key.isCtrlDown()) {
                openSelectedFile();
                return;
            }
            if (c == 'n' && key.isCtrlDown()) {
                addNewTab("untitled");
                focus = Focus.EDITOR;
                return;
            }
            if (c == 'w' && key.isCtrlDown()) {
                if (key.isAltDown()) {
                    closeTab();
                } else {
                    focus = focus == Focus.BROWSER ? Focus.EDITOR : Focus.BROWSER;
                }
                return;
            }
            if (c == 't' && key.isCtrlDown()) {
                addNewTab("untitled");
                focus = Focus.EDITOR;
                return;
            }
        }

        // Tab switching with Alt+number
        if (key.getKeyType() == KeyType.Character && key.isAltDown() && key.getCharacter() != null) {
            char c = key.getCharacter();
            if (c >= '1' && c <= '9') {
                int idx = c - '1';
                if (idx < tabs.size()) {
                    activeTab = idx;
                    focus = Focus.EDITOR;
                }
                return;
            }
        }

        // Tab switching with F-keys
        if (key.getKeyType() == KeyType.F1 && tabs.size() > 0) { activeTab = 0; focus = Focus.EDITOR; return; }
        if (key.getKeyType() == KeyType.F2 && tabs.size() > 1) { activeTab = 1; focus = Focus.EDITOR; return; }
        if (key.getKeyType() == KeyType.F3 && tabs.size() > 2) { activeTab = 2; focus = Focus.EDITOR; return; }
        if (key.getKeyType() == KeyType.F4 && tabs.size() > 3) { activeTab = 3; focus = Focus.EDITOR; return; }

        // Dispatch to focused component
        if (focus == Focus.BROWSER) {
            boolean handled = fileBrowser.handleInput(key);
            if (handled && key.getKeyType() == KeyType.Enter) {
                Path selected = fileBrowser.getSelectedPath();
                if (selected != null && !fileBrowser.isSelectedDirectory()) {
                    openFile(selected);
                }
            }
        } else if (focus == Focus.EDITOR) {
            if (!tabs.isEmpty()) {
                editorPanel.handleInput(key, tabs.get(activeTab));
            }
        }
    }

    private void openSelectedFile() {
        Path selected = fileBrowser.getSelectedPath();
        if (selected != null && !fileBrowser.isSelectedDirectory()) {
            openFile(selected);
        }
    }

    private void openFile(Path path) {
        try {
            String content = fileManager.readFile(path);
            TextBuffer buffer = new TextBuffer(content, path.toString());
            tabs.add(buffer);
            activeTab = tabs.size() - 1;
            focus = Focus.EDITOR;
        } catch (IOException e) {
            // Could show error, but keeping it simple
        }
    }

    private void addNewTab(String title) {
        TextBuffer buffer = new TextBuffer();
        tabs.add(buffer);
        activeTab = tabs.size() - 1;
    }

    private void closeTab() {
        if (tabs.isEmpty()) return;
        tabs.remove(activeTab);
        if (tabs.isEmpty()) {
            addNewTab("untitled");
        } else if (activeTab >= tabs.size()) {
            activeTab = tabs.size() - 1;
        }
    }

    private void saveCurrentFile() {
        if (tabs.isEmpty()) return;
        TextBuffer buffer = tabs.get(activeTab);
        try {
            if (buffer.getFilePath() != null) {
                fileManager.writeFile(Path.of(buffer.getFilePath()), buffer.getContent());
                buffer.setModified(false);
            } else {
                Path selected = fileBrowser.getSelectedPath();
                if (selected != null && !fileBrowser.isSelectedDirectory()) {
                    fileManager.writeFile(selected, buffer.getContent());
                    buffer.setFilePath(selected.toString());
                    buffer.setModified(false);
                }
                // If no path selected, we can't save - would need a "Save As" dialog
            }
        } catch (IOException e) {
            // Could show error
        }
    }

    private void cleanup() {
        if (screen != null) {
            try {
                screen.stopScreen();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
