package analyzer.fs.util;

public class ProgressBar {
    private final int total;
    private int current = 0;
    private final int width = 40;
    private final String label;

    public ProgressBar(int total, String label) {
        this.total = total;
        this.label = label;
        print();
    }

    public synchronized void increment() {
        current++;
        print();
    }

    private void print() {
        double pct = (double) current / total;
        int filled = (int) (width * pct);
        String bar = "█".repeat(filled) + "░".repeat(width - filled);
        System.out.printf("\r%s [%s] %d/%d (%.1f%%)", label, bar, current, total, pct * 100);
        if (current >= total) System.out.println();
    }
}
