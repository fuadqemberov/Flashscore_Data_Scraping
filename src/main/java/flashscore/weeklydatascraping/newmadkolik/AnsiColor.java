package flashscore.weeklydatascraping.newmadkolik;

/**
 * Konsola rəngli çıxış vermək üçün ANSI escape kodlarını saxlayan interfeys.
 * Windows 10 və daha yeni versiyaların terminalı, həmçinin əksər Linux/macOS terminalları bunu dəstəkləyir.
 */
public interface AnsiColor {
    String RESET = "\u001B[0m";

    String BOLD = "\u001B[1m";
    String UNDERLINE = "\u001B[4m";

    String BLACK = "\u001B[30m";
    String RED = "\u001B[31m";
    String GREEN = "\u001B[32m";
    String YELLOW = "\u001B[33m";
    String BLUE = "\u001B[34m";
    String PURPLE = "\u001B[35m";
    String CYAN = "\u001B[36m";
    String WHITE = "\u001B[37m";

    String BG_BLACK = "\u001B[40m";
    String BG_RED = "\u001B[41m";
    String BG_GREEN = "\u001B[42m";
    String BG_YELLOW = "\u001B[43m";
    String BG_BLUE = "\u001B[44m";
    String BG_PURPLE = "\u001B[45m";
    String BG_CYAN = "\u001B[46m";
    String BG_WHITE = "\u001B[47m";
}