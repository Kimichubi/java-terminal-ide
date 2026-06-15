package com.terminalide.ui;

import com.terminalide.file.FileManager;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileBrowser {
    private final FileManager fileManager;
    private Path rootPath;
    private FileNode rootNode;
    private final List<FileNode> flatList;
    private int selectedIndex;

    private static class FileNode {
        final String name;
        final Path path;
        final boolean isDirectory;
        final List<FileNode> children;
        boolean expanded;
        final int depth;

        FileNode(String name, Path path, boolean isDirectory, int depth) {
            this.name = name;
            this.path = path;
            this.isDirectory = isDirectory;
            this.children = new ArrayList<>();
            this.expanded = false;
            this.depth = depth;
        }
    }

    public FileBrowser(FileManager fileManager, String rootDir) {
        this.fileManager = fileManager;
        this.rootPath = Paths.get(rootDir).toAbsolutePath().normalize();
        this.flatList = new ArrayList<>();
        this.selectedIndex = 0;
        buildTree();
    }

    private void buildTree() {
        flatList.clear();
        rootNode = new FileNode(rootPath.getFileName().toString(), rootPath, true, 0);
        rootNode.expanded = true;
        flatList.add(rootNode);
        loadChildren(rootNode);
        flatten(rootNode);
    }

    private void loadChildren(FileNode node) {
        if (!node.isDirectory) return;
        try {
            List<Path> children = fileManager.listDirectory(node.path);
            for (Path p : children) {
                try {
                    boolean isDir = Files.isDirectory(p);
                    node.children.add(new FileNode(p.getFileName().toString(), p, isDir, node.depth + 1));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void flatten(FileNode node) {
        if (!node.expanded) return;
        for (FileNode child : node.children) {
            flatList.add(child);
            if (child.expanded) {
                flatten(child);
            }
        }
        // Rebuild flat list
        rebuildFlatList();
    }

    private void rebuildFlatList() {
        flatList.clear();
        flatList.add(rootNode);
        for (FileNode child : rootNode.children) {
            addToFlat(child);
        }
    }

    private void addToFlat(FileNode node) {
        flatList.add(node);
        if (node.expanded) {
            for (FileNode child : node.children) {
                addToFlat(child);
            }
        }
    }

    public void toggleExpand() {
        if (selectedIndex < 0 || selectedIndex >= flatList.size()) return;
        FileNode node = flatList.get(selectedIndex);
        if (!node.isDirectory) return;
        if (node.expanded) {
            collapseNode(node);
        } else {
            if (node.children.isEmpty()) {
                loadChildren(node);
            }
            node.expanded = true;
        }
        rebuildFlatList();
    }

    private void collapseNode(FileNode node) {
        for (FileNode child : node.children) {
            collapseNode(child);
        }
        node.expanded = false;
    }

    public void moveUp() {
        if (selectedIndex > 0) selectedIndex--;
    }

    public void moveDown() {
        if (selectedIndex < flatList.size() - 1) selectedIndex++;
    }

    public Path getSelectedPath() {
        if (selectedIndex >= 0 && selectedIndex < flatList.size()) {
            return flatList.get(selectedIndex).path;
        }
        return null;
    }

    public boolean isSelectedDirectory() {
        if (selectedIndex >= 0 && selectedIndex < flatList.size()) {
            return flatList.get(selectedIndex).isDirectory;
        }
        return false;
    }

    public void draw(TextGraphics tg, int startX, int startY, int width, int height, boolean focused) {
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);

        // Draw border
        drawBorder(tg, startX, startY, width, height, focused);

        int contentX = startX + 1;
        int contentY = startY + 1;
        int visibleLines = height - 2;
        int visibleWidth = width - 2;

        int scrollOffset = 0;
        if (selectedIndex >= visibleLines) {
            scrollOffset = selectedIndex - visibleLines + 1;
        }

        for (int i = 0; i < visibleLines; i++) {
            int flatIdx = scrollOffset + i;
            if (flatIdx >= flatList.size()) break;

            FileNode node = flatList.get(flatIdx);
            int drawY = contentY + i;

            if (flatIdx == selectedIndex) {
                if (focused) {
                    tg.setBackgroundColor(TextColor.ANSI.BLUE);
                    tg.setForegroundColor(TextColor.ANSI.WHITE);
                } else {
                    tg.setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT);
                    tg.setForegroundColor(TextColor.ANSI.WHITE);
                }
                for (int x = contentX; x < contentX + visibleWidth; x++) {
                    tg.setCharacter(x, drawY, ' ');
                }
            } else {
                tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
                tg.setForegroundColor(TextColor.ANSI.DEFAULT);
            }

            StringBuilder line = new StringBuilder();
            for (int d = 0; d < node.depth; d++) {
                line.append("  ");
            }
            if (node.isDirectory) {
                line.append(node.expanded ? "\u25BC " : "\u25B6 ");
            } else {
                line.append("  ");
            }
            line.append(node.name);
            if (node.isDirectory) line.append("/");

            String display = line.toString();
            if (display.length() > visibleWidth) {
                display = display.substring(0, visibleWidth);
            }
            tg.putString(contentX, drawY, display);
        }

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    private void drawBorder(TextGraphics tg, int x, int y, int w, int h, boolean focused) {
        TextColor color = focused ? TextColor.ANSI.BLUE : TextColor.ANSI.BLACK_BRIGHT;
        tg.setForegroundColor(color);
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);

        // Top
        tg.setCharacter(x, y, '\u250C');
        for (int i = 1; i < w - 1; i++) tg.setCharacter(x + i, y, '\u2500');
        tg.setCharacter(x + w - 1, y, '\u2510');

        // Sides
        for (int i = 1; i < h - 1; i++) {
            tg.setCharacter(x, y + i, '\u2502');
            tg.setCharacter(x + w - 1, y + i, '\u2502');
        }

        // Bottom
        tg.setCharacter(x, y + h - 1, '\u2514');
        for (int i = 1; i < w - 1; i++) tg.setCharacter(x + i, y + h - 1, '\u2500');
        tg.setCharacter(x + w - 1, y + h - 1, '\u2518');

        // Title
        String title = " Files ";
        int titleX = x + 2;
        if (titleX + title.length() < x + w) {
            tg.putString(titleX, y, title);
        }

        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    public boolean handleInput(KeyStroke key) {
        if (key.getKeyType() == KeyType.ArrowUp) {
            moveUp();
            return true;
        }
        if (key.getKeyType() == KeyType.ArrowDown) {
            moveDown();
            return true;
        }
        if (key.getKeyType() == KeyType.Enter || key.getKeyType() == KeyType.ArrowRight) {
            if (isSelectedDirectory()) {
                toggleExpand();
            }
            return true;
        }
        if (key.getKeyType() == KeyType.ArrowLeft) {
            if (selectedIndex >= 0 && selectedIndex < flatList.size()) {
                FileNode node = flatList.get(selectedIndex);
                if (node.expanded) {
                    toggleExpand();
                } else if (node.depth > 0) {
                    // Find parent
                    for (int i = selectedIndex - 1; i >= 0; i--) {
                        if (flatList.get(i).depth < node.depth) {
                            selectedIndex = i;
                            break;
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    public void setRootPath(Path rootPath) {
        this.rootPath = rootPath.toAbsolutePath().normalize();
        this.selectedIndex = 0;
        buildTree();
    }
}
