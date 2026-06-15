package com.terminalide;

import com.terminalide.ui.MainWindow;

public class App {
    public static void main(String[] args) {
        String initialPath = args.length > 0 ? args[0] : System.getProperty("user.dir");
        new MainWindow(initialPath).run();
    }
}
