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
import java.nio.file.Files;
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

    private enum Mode { NORMAL, SEARCH, GOTO_LINE }
    private Mode mode;
    private StringBuilder inputBuffer;
    private String lastSearchQuery;
    private boolean caseSensitiveSearch;
    private String clipboard;

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
        this.mode = Mode.NORMAL;
        this.inputBuffer = new StringBuilder();
        this.running = false;
        this.caseSensitiveSearch = false;
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
        screen.setCursorPosition(null);

        fileManager = new FileManager();
        fileBrowser = new FileBrowser(fileManager, initialPath);
        editorPanel = new EditorPanel();
        statusBar = new StatusBar();

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
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void updateDrawing() {
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);

        for (int y = 0; y < termHeight; y++) {
            for (int x = 0; x < termWidth; x++) {
                textGraphics.setCharacter(x, y, ' ');
            }
        }

        int browserW = Math.min(BROWSER_WIDTH, termWidth - MIN_EDITOR_WIDTH);
        int editorX = browserW;
        int editorW = termWidth - browserW;
        int statusY = termHeight - 1;
        int promptY = termHeight - 2;
        int mainHeight = mode == Mode.NORMAL ? termHeight - 1 : termHeight - 2;

        fileBrowser.draw(textGraphics, 0, 0, browserW, mainHeight, focus == Focus.BROWSER);

        drawTabBar(editorX, 0, editorW);
        if (!tabs.isEmpty()) {
            TextBuffer buffer = tabs.get(activeTab);
            String title = buffer.getFilePath() != null
                    ? Path.of(buffer.getFilePath()).getFileName().toString()
                    : "untitled";
            if (buffer.isModified()) title += " *";

            editorPanel.setSearchQuery(
                    mode == Mode.SEARCH ? inputBuffer.toString() : lastSearchQuery);
            editorPanel.draw(textGraphics, editorX, 1, editorW, mainHeight - 1, buffer, title, focus == Focus.EDITOR);
        }

        // Draw prompt line
        if (mode == Mode.SEARCH) {
            drawPrompt(promptY, editorX, editorW, "Search" + (caseSensitiveSearch ? " [Aa]" : " [..]"));
        } else if (mode == Mode.GOTO_LINE) {
            drawPrompt(promptY, editorX, editorW, "Go to line");
        }

        // Draw status bar
        if (!tabs.isEmpty()) {
            TextBuffer buffer = tabs.get(activeTab);
            String fileName = buffer.getFilePath() != null
                    ? Path.of(buffer.getFilePath()).getFileName().toString()
                    : "untitled";
            String modeStr = mode == Mode.SEARCH ? "SEARCH" : mode == Mode.GOTO_LINE ? "GOTO" : focus == Focus.EDITOR ? "EDIT" : "BROWSE";
            String lineInfo = String.format("%d:%d | %d lines", buffer.getCursorRow() + 1, buffer.getCursorCol() + 1, buffer.getLineCount());
            statusBar.draw(textGraphics, statusY, termWidth, modeStr, fileName, buffer.isModified(), lineInfo);
        } else {
            statusBar.draw(textGraphics, statusY, termWidth, "EXIT", "", false, "");
        }
    }

    private void drawPrompt(int y, int editorX, int editorW, String label) {
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT);
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        for (int x = editorX; x < editorX + editorW; x++) {
            textGraphics.setCharacter(x, y, ' ');
        }

        String prompt = " " + label + ": " + inputBuffer.toString();
        if (prompt.length() > editorW - 1) {
            prompt = prompt.substring(Math.max(0, prompt.length() - editorW + 2));
            prompt = "\u2026" + prompt.substring(1);
        }
        textGraphics.putString(editorX, y, prompt);
        // Draw cursor
        int cursorX = editorX + label.length() + 3 + inputBuffer.length();
        if (cursorX < editorX + editorW) {
            textGraphics.setBackgroundColor(TextColor.ANSI.WHITE);
            textGraphics.setForegroundColor(TextColor.ANSI.BLACK);
            textGraphics.setCharacter(cursorX, y, ' ');
            textGraphics.setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT);
            textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
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
        for (int tx = tabX; tx < x + width; tx++) {
            textGraphics.setCharacter(tx, y, ' ');
        }
    }

    private void handleGlobalInput(KeyStroke key) {
        // Prompt mode handling first
        if (mode == Mode.SEARCH) {
            handleSearchInput(key);
            return;
        }
        if (mode == Mode.GOTO_LINE) {
            handleGotoLineInput(key);
            return;
        }

        // NORMAL mode
        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            char c = key.getCharacter();
            if (key.isCtrlDown()) {
                switch (c) {
                    case 'q' -> { running = false; return; }
                    case 's' -> { saveCurrentFile(); return; }
                    case 'o' -> { openSelectedFile(); return; }
                    case 'n' -> { addNewTab("untitled"); focus = Focus.EDITOR; return; }
                    case 'w' -> {
                        if (key.isAltDown()) closeTab();
                        else focus = focus == Focus.BROWSER ? Focus.EDITOR : Focus.BROWSER;
                        return;
                    }
                    case 't' -> { addNewTab("untitled"); focus = Focus.EDITOR; return; }
                    case 'f' -> { enterSearchMode(); return; }
                    case 'g' -> { enterGotoLineMode(); return; }
                    case 'd' -> { dupeLine(); return; }
                    case 'z' -> { if (!tabs.isEmpty()) tabs.get(activeTab).undo(); return; }
                    case 'y' -> { if (!tabs.isEmpty()) tabs.get(activeTab).redo(); return; }
                    case 'c' -> { copySelection(); return; }
                    case 'x' -> { cutSelection(); return; }
                    case 'v' -> { pasteClipboard(); return; }
                    case 'a' -> { selectAll(); return; }
                }
            }
        }

        // F3: find next, Shift+F3: find previous
        if (key.getKeyType() == KeyType.F3) {
            if (lastSearchQuery != null && !lastSearchQuery.isEmpty() && !tabs.isEmpty()) {
                if (key.isShiftDown()) {
                    tabs.get(activeTab).findPrev(lastSearchQuery, caseSensitiveSearch);
                } else {
                    tabs.get(activeTab).findNext(lastSearchQuery, caseSensitiveSearch);
                }
            }
            return;
        }

        // Tab switching with Alt+number
        if (key.getKeyType() == KeyType.Character && key.isAltDown() && key.getCharacter() != null) {
            char c = key.getCharacter();
            if (c >= '1' && c <= '9') {
                int idx = c - '1';
                if (idx < tabs.size()) { activeTab = idx; focus = Focus.EDITOR; }
                return;
            }
        }

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

    private void enterSearchMode() {
        mode = Mode.SEARCH;
        inputBuffer.setLength(0);
    }

    private void enterGotoLineMode() {
        mode = Mode.GOTO_LINE;
        inputBuffer.setLength(0);
    }

    private void handleSearchInput(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            mode = Mode.NORMAL;
            inputBuffer.setLength(0);
            return;
        }
        if (key.getKeyType() == KeyType.Enter) {
            lastSearchQuery = inputBuffer.toString();
            if (!lastSearchQuery.isEmpty() && !tabs.isEmpty()) {
                tabs.get(activeTab).findNext(lastSearchQuery, caseSensitiveSearch);
            }
            mode = Mode.NORMAL;
            return;
        }
        if (key.getKeyType() == KeyType.Backspace) {
            if (!inputBuffer.isEmpty()) inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            if (!inputBuffer.isEmpty() && !tabs.isEmpty()) {
                tabs.get(activeTab).findPrev(inputBuffer.toString(), caseSensitiveSearch);
            }
            return;
        }
        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            char c = key.getCharacter();
            if (c == 's' && key.isCtrlDown()) {
                caseSensitiveSearch = !caseSensitiveSearch;
                return;
            }
            inputBuffer.append(c);
            if (!tabs.isEmpty()) {
                tabs.get(activeTab).findNext(inputBuffer.toString(), caseSensitiveSearch);
            }
            return;
        }
        if (key.getKeyType() == KeyType.ArrowDown && !tabs.isEmpty()) {
            tabs.get(activeTab).findNext(inputBuffer.toString(), caseSensitiveSearch);
        }
        if (key.getKeyType() == KeyType.ArrowUp && !tabs.isEmpty()) {
            tabs.get(activeTab).findPrev(inputBuffer.toString(), caseSensitiveSearch);
        }
    }

    private void handleGotoLineInput(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            mode = Mode.NORMAL;
            inputBuffer.setLength(0);
            return;
        }
        if (key.getKeyType() == KeyType.Enter) {
            try {
                int line = Integer.parseInt(inputBuffer.toString());
                if (line > 0 && !tabs.isEmpty()) {
                    tabs.get(activeTab).setCursorRow(line - 1);
                    tabs.get(activeTab).moveToLineStart();
                }
            } catch (NumberFormatException ignored) {}
            mode = Mode.NORMAL;
            inputBuffer.setLength(0);
            return;
        }
        if (key.getKeyType() == KeyType.Backspace) {
            if (!inputBuffer.isEmpty()) inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            return;
        }
        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            char c = key.getCharacter();
            if (c >= '0' && c <= '9') {
                inputBuffer.append(c);
            }
            return;
        }
    }

    private void dupeLine() {
        if (tabs.isEmpty()) return;
        TextBuffer b = tabs.get(activeTab);
        String line = b.getLine(b.getCursorRow());
        b.moveToLineEnd();
        b.newLine();
        b.insertString(line);
    }

    private void copySelection() {
        if (tabs.isEmpty()) return;
        TextBuffer b = tabs.get(activeTab);
        if (b.hasSelection()) clipboard = b.getSelectedText();
        else clipboard = b.getLine(b.getCursorRow());
    }

    private void cutSelection() {
        if (tabs.isEmpty()) return;
        TextBuffer b = tabs.get(activeTab);
        if (b.hasSelection()) {
            clipboard = b.getSelectedText();
            b.deleteSelected();
        }
    }

    private void pasteClipboard() {
        if (tabs.isEmpty() || clipboard == null) return;
        TextBuffer b = tabs.get(activeTab);
        if (b.hasSelection()) b.deleteSelected();
        b.insertText(clipboard);
    }

    private void selectAll() {
        if (tabs.isEmpty()) return;
        TextBuffer b = tabs.get(activeTab);
        b.moveToStart();
        b.startSelection();
        b.moveToEnd();
    }

    private void openSelectedFile() {
        Path selected = fileBrowser.getSelectedPath();
        if (selected != null && !fileBrowser.isSelectedDirectory()) {
            openFile(selected);
        }
    }

    private void openFile(Path path) {
        try {
            // Check if already open
            String pathStr = path.toString();
            for (int i = 0; i < tabs.size(); i++) {
                if (pathStr.equals(tabs.get(i).getFilePath())) {
                    activeTab = i;
                    focus = Focus.EDITOR;
                    return;
                }
            }
            String content = fileManager.readFile(path);
            TextBuffer buffer = new TextBuffer(content, pathStr);
            tabs.add(buffer);
            activeTab = tabs.size() - 1;
            focus = Focus.EDITOR;
        } catch (IOException e) {
            // Could show error
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
            }
        } catch (IOException e) {
            // Could show error
        }
    }

    private void cleanup() {
        if (screen != null) {
            try { screen.stopScreen(); } catch (IOException e) { e.printStackTrace(); }
        }
        if (terminal != null) {
            try { terminal.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }
}
