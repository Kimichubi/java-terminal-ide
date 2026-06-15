package com.terminalide.editor;

import java.util.ArrayList;
import java.util.List;

public class TextBuffer {
    private final List<StringBuilder> lines;
    private int cursorRow;
    private int cursorCol;
    private int scrollRow;
    private int scrollCol;
    private boolean modified;
    private String filePath;

    public TextBuffer() {
        this.lines = new ArrayList<>();
        this.lines.add(new StringBuilder());
        this.cursorRow = 0;
        this.cursorCol = 0;
        this.scrollRow = 0;
        this.scrollCol = 0;
        this.modified = false;
    }

    public TextBuffer(String content, String filePath) {
        this();
        this.filePath = filePath;
        String[] split = content.split("\n", -1);
        for (String s : split) {
            lines.add(new StringBuilder(s));
        }
        if (!content.isEmpty() && content.endsWith("\n")) {
            lines.add(new StringBuilder());
        }
    }

    public void insertChar(char c) {
        lines.get(cursorRow).insert(cursorCol, c);
        cursorCol++;
        modified = true;
    }

    public void insertString(String s) {
        lines.get(cursorRow).insert(cursorCol, s);
        cursorCol += s.length();
        modified = true;
    }

    public void backspace() {
        if (cursorCol > 0) {
            lines.get(cursorRow).deleteCharAt(cursorCol - 1);
            cursorCol--;
            modified = true;
        } else if (cursorRow > 0) {
            cursorCol = lines.get(cursorRow - 1).length();
            lines.get(cursorRow - 1).append(lines.get(cursorRow));
            lines.remove(cursorRow);
            cursorRow--;
            modified = true;
        }
    }

    public void delete() {
        if (cursorCol < lines.get(cursorRow).length()) {
            lines.get(cursorRow).deleteCharAt(cursorCol);
            modified = true;
        } else if (cursorRow < lines.size() - 1) {
            lines.get(cursorRow).append(lines.get(cursorRow + 1));
            lines.remove(cursorRow + 1);
            modified = true;
        }
    }

    public void newLine() {
        String rest = lines.get(cursorRow).substring(cursorCol);
        lines.get(cursorRow).setLength(cursorCol);
        lines.add(cursorRow + 1, new StringBuilder(rest));
        cursorRow++;
        cursorCol = 0;
        modified = true;
    }

    public int getLineCount() {
        return lines.size();
    }

    public String getLine(int index) {
        return lines.get(index).toString();
    }

    public int getLineLength(int index) {
        return lines.get(index).length();
    }

    public String getContent() {
        return String.join("\n", lines);
    }

    public int getCursorRow() { return cursorRow; }
    public int getCursorCol() { return cursorCol; }
    public int getScrollRow() { return scrollRow; }
    public int getScrollCol() { return scrollCol; }

    public void setCursorRow(int row) {
        this.cursorRow = clamp(row, 0, lines.size() - 1);
        clampCursorCol();
    }

    public void setCursorCol(int col) {
        this.cursorCol = clamp(col, 0, lines.get(cursorRow).length());
    }

    public void moveCursor(int dRow, int dCol) {
        if (dRow != 0) {
            setCursorRow(cursorRow + dRow);
        }
        if (dCol != 0) {
            setCursorCol(cursorCol + dCol);
        }
    }

    public void moveToLineStart() { cursorCol = 0; }
    public void moveToLineEnd() { cursorCol = lines.get(cursorRow).length(); }

    public void moveToStart() { cursorRow = 0; cursorCol = 0; }
    public void moveToEnd() { cursorRow = lines.size() - 1; cursorCol = lines.get(cursorRow).length(); }

    public void moveWordLeft() {
        String line = getLine(cursorRow);
        while (cursorCol > 0 && !isWordChar(line.charAt(cursorCol - 1))) cursorCol--;
        while (cursorCol > 0 && isWordChar(line.charAt(cursorCol - 1))) cursorCol--;
    }

    public void moveWordRight() {
        String line = getLine(cursorRow);
        while (cursorCol < line.length() && !isWordChar(line.charAt(cursorCol))) cursorCol++;
        while (cursorCol < line.length() && isWordChar(line.charAt(cursorCol))) cursorCol++;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public boolean findNext(String query, boolean caseSensitive) {
        if (query.isEmpty()) return false;
        int startCol = cursorCol;
        for (int r = cursorRow; r < lines.size(); r++) {
            int idx = indexOf(lines.get(r).toString(), query, caseSensitive, r == cursorRow ? startCol : 0);
            if (idx >= 0) { cursorRow = r; cursorCol = idx + query.length(); return true; }
            startCol = 0;
        }
        for (int r = 0; r <= cursorRow; r++) {
            int idx = indexOf(lines.get(r).toString(), query, caseSensitive, 0);
            int limit = (r == cursorRow) ? cursorCol : Integer.MAX_VALUE;
            if (idx >= 0 && idx < limit) { cursorRow = r; cursorCol = idx + query.length(); return true; }
        }
        return false;
    }

    public boolean findPrev(String query, boolean caseSensitive) {
        if (query.isEmpty()) return false;
        int startCol = cursorCol - 1;
        for (int r = cursorRow; r >= 0; r--) {
            int idx = lastIndexOf(lines.get(r).toString(), query, caseSensitive, r == cursorRow ? startCol : Integer.MAX_VALUE);
            if (idx >= 0) { cursorRow = r; cursorCol = idx; return true; }
            startCol = Integer.MAX_VALUE;
        }
        for (int r = lines.size() - 1; r >= cursorRow; r--) {
            int idx = lastIndexOf(lines.get(r).toString(), query, caseSensitive, Integer.MAX_VALUE);
            int limit = (r == cursorRow) ? cursorCol : -1;
            if (idx >= 0 && idx >= limit) { cursorRow = r; cursorCol = idx; return true; }
        }
        return false;
    }

    private static int indexOf(String line, String query, boolean caseSensitive, int from) {
        String s = caseSensitive ? line : line.toLowerCase();
        String q = caseSensitive ? query : query.toLowerCase();
        return s.indexOf(q, from);
    }

    private static int lastIndexOf(String line, String query, boolean caseSensitive, int from) {
        String s = caseSensitive ? line : line.toLowerCase();
        String q = caseSensitive ? query : query.toLowerCase();
        int idx = -1;
        int pos = 0;
        while ((pos = s.indexOf(q, pos)) >= 0 && (from < 0 || pos <= from)) {
            idx = pos;
            pos++;
        }
        return idx;
    }

    public void updateScroll(int visibleRows, int visibleCols) {
        if (cursorRow < scrollRow) scrollRow = cursorRow;
        if (cursorRow >= scrollRow + visibleRows) scrollRow = cursorRow - visibleRows + 1;
        if (cursorCol < scrollCol) scrollCol = cursorCol;
        if (cursorCol >= scrollCol + visibleCols) scrollCol = cursorCol - visibleCols + 1;
        scrollRow = Math.max(0, scrollRow);
        scrollCol = Math.max(0, scrollCol);
    }

    public boolean isModified() { return modified; }
    public void setModified(boolean modified) { this.modified = modified; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    private void clampCursorCol() {
        cursorCol = clamp(cursorCol, 0, lines.get(cursorRow).length());
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
