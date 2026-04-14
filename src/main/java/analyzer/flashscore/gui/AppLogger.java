package analyzer.flashscore.gui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class AppLogger {
    private static TextArea consoleArea;

    public static void setConsoleArea(TextArea textArea) {
        consoleArea = textArea;
    }

    public static void log(String message) {
        System.out.println(message); // Arka plan konsolu için
        if (consoleArea != null) {
            // UI thread'inde güvenli bir şekilde metin ekler
            Platform.runLater(() -> {
                consoleArea.appendText(message + "\n");
                // Otomatik olarak en alta scroll yapar
                consoleArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }
}