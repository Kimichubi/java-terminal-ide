package com.terminalide.ui;

import com.terminalide.editor.TextBuffer;
import com.terminalide.syntax.SyntaxHighlighter;
import com.terminalide.syntax.SyntaxHighlighter.Token;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class EditorPanel {
    private final SyntaxHighlighter highlighter;
    private String searchQuery;

    public EditorPanel() {
        this.highlighter = new SyntaxHighlighter();
    }

    public void setSearchQuery(String query) { this.searchQuery = query; }

    public void draw(TextGraphics tg, int startX, int startY, int width, int height,
                      TextBuffer buffer, String tabTitle, boolean focused) {
        drawBorder(tg, startX, startY, width, height, tabTitle, focused);

        int contentX = startX + 1;
        int contentY = startY + 1;
        int visibleRows = height - 2;
        int visibleCols = width - 2 - 5; // 5 for line numbers

        buffer.updateScroll(visibleRows, visibleCols);

        int lineNumWidth = 4;
        int textStartX = contentX + lineNumWidth + 1; // +1 for separator

        for (int i = 0; i < visibleRows; i++) {
            int lineIdx = buffer.getScrollRow() + i;
            if (lineIdx >= buffer.getLineCount()) break;
            int drawY = contentY + i;

            // Line number
            String num = String.format("%4d", lineIdx + 1);
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
            if (lineIdx == buffer.getCursorRow() && focused) {
                tg.setForegroundColor(TextColor.ANSI.YELLOW);
            }
            tg.putString(contentX, drawY, num);

            // Separator
            tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            tg.setCharacter(textStartX - 1, drawY, '\u2502');

            // Text line with syntax + search highlights
            String line = buffer.getLine(lineIdx);
            int visibleStart = buffer.getScrollCol();
            String visibleText = line.substring(Math.min(visibleStart, line.length()));
            if (visibleText.length() > visibleCols) {
                visibleText = visibleText.substring(0, visibleCols);
            }

            tg.setBackgroundColor(TextColor.ANSI.DEFAULT);

            if (visibleText.isEmpty()) {
                tg.setForegroundColor(TextColor.ANSI.DEFAULT);
                tg.putString(textStartX, drawY, " ");
            } else {
                var tokens = highlighter.tokenize(visibleText);
                int pos = 0;
                for (Token token : tokens) {
                    int tokenStart = Math.max(0, token.start() - visibleStart);
                    int tokenEnd = Math.min(visibleText.length(), token.end() - visibleStart);
                    if (tokenStart > pos) {
                        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
                        tg.putString(textStartX + pos, drawY, visibleText.substring(pos, tokenStart));
                    }
                    if (tokenStart < tokenEnd) {
                        tg.setForegroundColor(highlighter.colorFor(token.type()));
                        tg.putString(textStartX + tokenStart, drawY, visibleText.substring(tokenStart, tokenEnd));
                    }
                    pos = tokenEnd;
                }
                if (pos < visibleText.length()) {
                    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
                    tg.putString(textStartX + pos, drawY, visibleText.substring(pos));
                }

                // Highlight search matches
                if (searchQuery != null && !searchQuery.isEmpty()) {
                    String lowerLine = visibleText.toLowerCase();
                    String lowerQuery = searchQuery.toLowerCase();
                    int idx = 0;
                    while ((idx = lowerLine.indexOf(lowerQuery, idx)) >= 0) {
                        tg.setBackgroundColor(TextColor.ANSI.YELLOW);
                        tg.setForegroundColor(TextColor.ANSI.BLACK);
                        for (int j = 0; j < lowerQuery.length(); j++) {
                            int cx = textStartX + idx + j;
                            tg.setCharacter(cx, drawY, visibleText.charAt(idx + j));
                        }
                        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
                        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
                        idx += lowerQuery.length();
                    }
                }

                // Highlight selection
                if (focused && buffer.hasSelection()) {
                    int selStartRow = buffer.getSelStartRow();
                    int selEndRow = buffer.getSelEndRow();
                    if (lineIdx >= selStartRow && lineIdx <= selEndRow) {
                        int selStart = (lineIdx == selStartRow) ? buffer.getSelStartCol() : 0;
                        int selEnd = (lineIdx == selEndRow) ? buffer.getSelEndCol() : line.length();
                        selStart = Math.max(selStart - visibleStart, 0);
                        selEnd = Math.min(selEnd - visibleStart, visibleCols);
                        if (selEnd > selStart) {
                            tg.setBackgroundColor(TextColor.ANSI.CYAN);
                            tg.setForegroundColor(TextColor.ANSI.BLACK);
                            for (int s = selStart; s < selEnd; s++) {
                                int cx = textStartX + s;
                                int si = visibleStart + s;
                                char ch = si < line.length() ? line.charAt(si) : ' ';
                                tg.setCharacter(cx, drawY, ch);
                            }
                            tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
                            tg.setForegroundColor(TextColor.ANSI.DEFAULT);
                        }
                    }
                }
            }
        }

        // Cursor
        if (focused) {
            int cursorDrawY = contentY + buffer.getCursorRow() - buffer.getScrollRow();
            int cursorDrawX = textStartX + buffer.getCursorCol() - buffer.getScrollCol();
            if (cursorDrawY >= contentY && cursorDrawY < contentY + visibleRows
                    && cursorDrawX >= textStartX && cursorDrawX < textStartX + visibleCols) {
                tg.setBackgroundColor(TextColor.ANSI.WHITE);
                tg.setForegroundColor(TextColor.ANSI.BLACK);

                int lineIdx = buffer.getCursorRow();
                String line = buffer.getLine(lineIdx);
                int col = buffer.getCursorCol();
                char cursorChar = col < line.length() ? line.charAt(col) : ' ';
                tg.setCharacter(cursorDrawX, cursorDrawY, cursorChar);
            }
        }

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    private void drawBorder(TextGraphics tg, int x, int y, int w, int h, String title, boolean focused) {
        TextColor color = focused ? TextColor.ANSI.BLUE : TextColor.ANSI.BLACK_BRIGHT;
        tg.setForegroundColor(color);
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);

        tg.setCharacter(x, y, '\u250C');
        for (int i = 1; i < w - 1; i++) tg.setCharacter(x + i, y, '\u2500');
        tg.setCharacter(x + w - 1, y, '\u2510');

        for (int i = 1; i < h - 1; i++) {
            tg.setCharacter(x, y + i, '\u2502');
            tg.setCharacter(x + w - 1, y + i, '\u2502');
        }

        tg.setCharacter(x, y + h - 1, '\u2514');
        for (int i = 1; i < w - 1; i++) tg.setCharacter(x + i, y + h - 1, '\u2500');
        tg.setCharacter(x + w - 1, y + h - 1, '\u2518');

        String t = " " + title + " ";
        if (x + 1 + t.length() < x + w) {
            tg.putString(x + 1, y, t);
        }

        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    public boolean handleInput(KeyStroke key, TextBuffer buffer) {
        boolean shift = key.isShiftDown();
        boolean ctrl = key.isCtrlDown();

        if (key.getKeyType() == KeyType.Character) {
            Character c = key.getCharacter();
            if (c != null && !ctrl && !key.isAltDown()) {
                if (buffer.hasSelection()) buffer.deleteSelected();
                buffer.insertChar(c);
                return true;
            }
        }

        switch (key.getKeyType()) {
            case Enter -> {
                if (buffer.hasSelection()) buffer.deleteSelected();
                buffer.newLine();
                return true;
            }
            case Backspace -> {
                if (buffer.hasSelection()) { buffer.deleteSelected(); return true; }
                buffer.backspace();
                return true;
            }
            case Delete -> {
                if (buffer.hasSelection()) { buffer.deleteSelected(); return true; }
                buffer.delete();
                return true;
            }
            case ArrowUp -> {
                if (!shift) buffer.clearSelection();
                else buffer.extendSelection();
                buffer.moveCursor(-1, 0);
                return true;
            }
            case ArrowDown -> {
                if (!shift) buffer.clearSelection();
                else buffer.extendSelection();
                buffer.moveCursor(1, 0);
                return true;
            }
            case ArrowLeft -> {
                if (!shift) buffer.clearSelection();
                else buffer.extendSelection();
                if (ctrl) buffer.moveWordLeft();
                else buffer.moveCursor(0, -1);
                return true;
            }
            case ArrowRight -> {
                if (!shift) buffer.clearSelection();
                else buffer.extendSelection();
                if (ctrl) buffer.moveWordRight();
                else buffer.moveCursor(0, 1);
                return true;
            }
            case Home -> {
                if (!shift) buffer.clearSelection();
                else buffer.extendSelection();
                if (ctrl) buffer.moveToStart();
                else buffer.moveToLineStart();
                return true;
            }
            case End -> {
                if (!shift) buffer.clearSelection();
                else buffer.extendSelection();
                if (ctrl) buffer.moveToEnd();
                else buffer.moveToLineEnd();
                return true;
            }
            case PageUp -> {
                if (!shift) buffer.clearSelection();
                else buffer.extendSelection();
                for (int i = 0; i < 20; i++) buffer.moveCursor(-1, 0);
                return true;
            }
            case PageDown -> {
                if (!shift) buffer.clearSelection();
                else buffer.extendSelection();
                for (int i = 0; i < 20; i++) buffer.moveCursor(1, 0);
                return true;
            }
        }
        return false;
    }
}
