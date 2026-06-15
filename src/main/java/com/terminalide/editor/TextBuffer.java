package com.terminalide.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class TextBuffer {
    private final List<StringBuilder> lines;
    private int cursorRow;
    private int cursorCol;
    private int scrollRow;
    private int scrollCol;
    private boolean modified;
    private String filePath;

    // Selection state
    private int selAnchorRow = -1;
    private int selAnchorCol;
    private boolean selecting;

    private record Snapshot(String[] content, int row, int col) {}
    private final Deque<Snapshot> undoStack;
    private final Deque<Snapshot> redoStack;
    private static final int MAX_UNDO = 500;

    public TextBuffer() {
        this.lines = new ArrayList<>();
        this.lines.add(new StringBuilder());
        this.cursorRow = 0;
        this.cursorCol = 0;
        this.scrollRow = 0;
        this.scrollCol = 0;
        this.modified = false;
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
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

    private void pushUndo() {
        if (undoStack.size() >= MAX_UNDO) undoStack.removeLast();
        String[] content = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) content[i] = lines.get(i).toString();
        undoStack.push(new Snapshot(content, cursorRow, cursorCol));
        redoStack.clear();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(new Snapshot(snapshot(), cursorRow, cursorCol));
        restoreFrom(undoStack.pop());
        modified = true;
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(new Snapshot(snapshot(), cursorRow, cursorCol));
        restoreFrom(redoStack.pop());
        modified = true;
    }

    private String[] snapshot() {
        String[] content = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) content[i] = lines.get(i).toString();
        return content;
    }

    private void restoreFrom(Snapshot s) {
        lines.clear();
        for (String line : s.content) lines.add(new StringBuilder(line));
        cursorRow = clamp(s.row, 0, lines.size() - 1);
        cursorCol = clamp(s.col, 0, lines.get(cursorRow).length());
    }

    public void insertChar(char c) {
        pushUndo();
        lines.get(cursorRow).insert(cursorCol, c);
        cursorCol++;
        modified = true;
    }

    public void insertString(String s) {
        pushUndo();
        lines.get(cursorRow).insert(cursorCol, s);
        cursorCol += s.length();
        modified = true;
    }

    public void backspace() {
        pushUndo();
        if (cursorCol > 0) {
            lines.get(cursorRow).deleteCharAt(cursorCol - 1);
            cursorCol--;
        } else if (cursorRow > 0) {
            cursorCol = lines.get(cursorRow - 1).length();
            lines.get(cursorRow - 1).append(lines.get(cursorRow));
            lines.remove(cursorRow);
            cursorRow--;
        }
        modified = true;
    }

    public void delete() {
        pushUndo();
        if (cursorCol < lines.get(cursorRow).length()) {
            lines.get(cursorRow).deleteCharAt(cursorCol);
        } else if (cursorRow < lines.size() - 1) {
            lines.get(cursorRow).append(lines.get(cursorRow + 1));
            lines.remove(cursorRow + 1);
        }
        modified = true;
    }

    public void newLine() {
        pushUndo();
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
        if (dRow != 0) setCursorRow(cursorRow + dRow);
        if (dCol != 0) setCursorCol(cursorCol + dCol);
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
            int idx = indexOf(getLine(r), query, caseSensitive, r == cursorRow ? startCol : 0);
            if (idx >= 0) { cursorRow = r; cursorCol = idx + query.length(); return true; }
            startCol = 0;
        }
        for (int r = 0; r <= cursorRow; r++) {
            int idx = indexOf(getLine(r), query, caseSensitive, 0);
            int limit = (r == cursorRow) ? cursorCol : Integer.MAX_VALUE;
            if (idx >= 0 && idx < limit) { cursorRow = r; cursorCol = idx + query.length(); return true; }
        }
        return false;
    }

    public boolean findPrev(String query, boolean caseSensitive) {
        if (query.isEmpty()) return false;
        int startCol = cursorCol - 1;
        for (int r = cursorRow; r >= 0; r--) {
            int idx = lastIndexOf(getLine(r), query, caseSensitive, r == cursorRow ? startCol : Integer.MAX_VALUE);
            if (idx >= 0) { cursorRow = r; cursorCol = idx; return true; }
            startCol = Integer.MAX_VALUE;
        }
        for (int r = lines.size() - 1; r >= cursorRow; r--) {
            int idx = lastIndexOf(getLine(r), query, caseSensitive, Integer.MAX_VALUE);
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
        int idx = -1, pos = 0;
        while ((pos = s.indexOf(q, pos)) >= 0 && (from < 0 || pos <= from)) {
            idx = pos;
            pos++;
        }
        return idx;
    }

    public void startSelection() {
        selAnchorRow = cursorRow;
        selAnchorCol = cursorCol;
        selecting = true;
    }

    public void extendSelection() {
        if (!selecting) startSelection();
    }

    public void clearSelection() { selecting = false; selAnchorRow = -1; }
    public boolean hasSelection() {
        return selecting && !(selAnchorRow == cursorRow && selAnchorCol == cursorCol);
    }

    public int getSelStartRow() {
        return Math.min(selAnchorRow, cursorRow);
    }

    public int getSelStartCol() {
        if (selAnchorRow < cursorRow) return selAnchorCol;
        if (selAnchorRow > cursorRow) return cursorCol;
        return Math.min(selAnchorCol, cursorCol);
    }

    public int getSelEndRow() {
        return Math.max(selAnchorRow, cursorRow);
    }

    public int getSelEndCol() {
        if (selAnchorRow < cursorRow) return cursorCol;
        if (selAnchorRow > cursorRow) return selAnchorCol;
        return Math.max(selAnchorCol, cursorCol);
    }

    public String getSelectedText() {
        if (!hasSelection()) return "";
        return getTextRange(getSelStartRow(), getSelStartCol(), getSelEndRow(), getSelEndCol());
    }

    public void deleteSelected() {
        if (!hasSelection()) return;
        deleteRange(getSelStartRow(), getSelStartCol(), getSelEndRow(), getSelEndCol());
        clearSelection();
    }

    public void insertText(String text) {
        if (hasSelection()) deleteSelected();
        if (text.isEmpty()) return;
        pushUndo();
        String[] parts = text.split("\n", -1);
        String rest = lines.get(cursorRow).substring(cursorCol);
        lines.get(cursorRow).setLength(cursorCol);
        lines.get(cursorRow).append(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            lines.add(cursorRow + i, new StringBuilder(parts[i]));
        }
        int lastIdx = cursorRow + parts.length - 1;
        lines.get(lastIdx).append(rest);
        cursorRow = lastIdx;
        cursorCol = parts[parts.length - 1].length();
        redoStack.clear();
        modified = true;
    }

    public String getIndent(int row) {
        String line = getLine(row);
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    public void insertTab() {
        if (hasSelection()) {
            indentSelection();
        } else {
            insertString("    ");
        }
    }

    private void indentSelection() {
        pushUndo();
        int start = getSelStartRow();
        int end = getSelEndRow();
        for (int i = start; i <= end; i++) {
            lines.get(i).insert(0, "    ");
        }
        if (selAnchorCol >= 0) selAnchorCol += 4;
        cursorCol += 4;
        modified = true;
    }

    public void dedent() {
        if (hasSelection()) {
            dedentSelection();
        } else {
            String indent = getIndent(cursorRow);
            if (indent.isEmpty()) return;
            pushUndo();
            int remove = Math.min(4, indent.length());
            lines.get(cursorRow).delete(0, remove);
            cursorCol = Math.max(0, cursorCol - remove);
            modified = true;
        }
    }

    private void dedentSelection() {
        pushUndo();
        int start = getSelStartRow();
        int end = getSelEndRow();
        for (int i = start; i <= end; i++) {
            String indent = getIndent(i);
            int remove = Math.min(4, indent.length());
            if (remove > 0) lines.get(i).delete(0, remove);
        }
        modified = true;
    }

    public void autoIndentNewLine() {
        pushUndo();
        String indent = getIndent(cursorRow);
        String rest = lines.get(cursorRow).substring(cursorCol);
        lines.get(cursorRow).setLength(cursorCol);
        // Copy indent to new line
        lines.add(cursorRow + 1, new StringBuilder(indent + rest));
        cursorRow++;
        cursorCol = indent.length();
        redoStack.clear();
        modified = true;
    }

    public void dedentClosingBrace() {
        if (cursorCol == 0 && cursorRow > 0) {
            String indent = getIndent(cursorRow);
            if (indent.length() >= 4) {
                pushUndo();
                lines.get(cursorRow).delete(0, 4);
                cursorCol = 0;
                modified = true;
            }
        }
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

    public void deleteRange(int startRow, int startCol, int endRow, int endCol) {
        pushUndo();
        if (startRow == endRow) {
            lines.get(startRow).delete(startCol, endCol);
        } else {
            String endRest = lines.get(endRow).substring(endCol);
            lines.get(startRow).setLength(startCol);
            lines.get(startRow).append(endRest);
            for (int i = endRow; i > startRow; i--) lines.remove(i);
        }
        cursorRow = startRow;
        cursorCol = startCol;
        modified = true;
    }

    public String getTextRange(int startRow, int startCol, int endRow, int endCol) {
        if (startRow == endRow) {
            return lines.get(startRow).substring(startCol, endCol);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(lines.get(startRow).substring(startCol)).append("\n");
        for (int i = startRow + 1; i < endRow; i++) sb.append(lines.get(i)).append("\n");
        sb.append(lines.get(endRow), 0, endCol);
        return sb.toString();
    }
}
