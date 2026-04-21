package analyzer.flashscore;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class AppLogger {
    private static TextArea consoleArea;
    private static long lastLogTime = 0;

    public static void setConsoleArea(TextArea textArea) {
        consoleArea = textArea;
    }

    public static void log(String message) {
        System.out.println(Thread.currentThread().getName() + " - " + message);

        if (consoleArea != null) {
            Platform.runLater(() -> {
                consoleArea.appendText(message + "\n");
                // Her logda değil, belirli aralıklarla scroll yaparak CPU kurtar.
                long now = System.currentTimeMillis();
                if (now - lastLogTime > 500) { // Saniyede en fazla 2 kere scroll yap
                    consoleArea.setScrollTop(Double.MAX_VALUE);
                    lastLogTime = now;
                }
            });
        }
    }
}