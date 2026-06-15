package com.terminalide.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class StatusBar {

    public void draw(TextGraphics tg, int row, int width, String mode, String fileName, boolean modified, String extraInfo) {
        tg.setBackgroundColor(TextColor.ANSI.BLUE);
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        for (int x = 0; x < width; x++) {
            tg.setCharacter(x, row, ' ');
        }

        String left = String.format(" %s | %s%s ", mode, fileName != null ? fileName : "untitled", modified ? " [+]" : "");
        tg.putString(0, row, left);
        if (left.length() > width) return;

        String right = extraInfo != null ? extraInfo : "Ctrl+Q:Quit  Ctrl+S:Save  Ctrl+O:Open  Ctrl+N:New  Ctrl+W:Switch";
        int rightX = width - right.length() - 1;
        if (rightX > left.length()) {
            tg.putString(rightX, row, right);
        }
    }
}
