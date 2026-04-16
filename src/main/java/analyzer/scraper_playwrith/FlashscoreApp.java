package analyzer.scraper_playwrith;

import com.microsoft.playwright.Page;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FlashscoreApp extends Application {

    private final CopyOnWriteArrayList<MatchData> resultList = new CopyOnWriteArrayList<>();
    private final AtomicInteger doneCount = new AtomicInteger(0);
    private final AtomicLong startTimeMs = new AtomicLong(0);

    private final ScheduledExecutorService timerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "elapsed-timer");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> timerFuture;

    // UI Bileşenleri
    private ComboBox<Integer> daysCombo;
    private TextField pathField;
    private Button startBtn;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Label timerLabel;
    private TextArea logArea;
    private File selectedSaveFile;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("FlashScore Bet365 Enterprise v3.0");

        // ── BAŞLIK ──────────────────────────────────────────────────────
        Label titleLabel = new Label("FlashScore Bet365 Bot");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // ── GÜN SEÇİMİ ──────────────────────────────────────────────────
        HBox daysBox = new HBox(10);
        daysBox.setAlignment(Pos.CENTER_LEFT);
        daysCombo = new ComboBox<>();
        daysCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7);
        daysCombo.setValue(1);
        daysBox.getChildren().addAll(new Label("Taranacak Gün Sayısı:"), daysCombo);

        // ── DOSYA SEÇİMİ ─────────────────────────────────────────────────
        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        pathField = new TextField();
        pathField.setEditable(false);
        pathField.setPrefWidth(250);
        Button browseBtn = new Button("Gözat...");
        browseBtn.setOnAction(e -> selectSaveLocation(primaryStage));
        fileBox.getChildren().addAll(new Label("Kaydedilecek Yer:"), pathField, browseBtn);

        // ── PROGRESS + STATUS + TIMER ────────────────────────────────────
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setPrefHeight(20);

        statusLabel = new Label("Bekleniyor...");
        timerLabel = new Label("⏱  00:00:00");
        timerLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 13px; -fx-text-fill: #7f8c8d;");

        HBox statusRow = new HBox(20);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getChildren().addAll(statusLabel, timerLabel);

        // ── LOG ALANI (NEON YEŞİL) ───────────────────────────────────────
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        logArea.setStyle("-fx-control-inner-background:#1e1e1e; -fx-text-fill:#00ff00; -fx-font-family:'Consolas';");
        AppLogger.setConsoleArea(logArea); // Logger'ı bağla

        // ── BAŞLAT BUTONU ───────────────────────────────────────────────
        startBtn = new Button("TARAMAYI BAŞLAT");
        startBtn.setStyle("-fx-background-color:#27ae60; -fx-text-fill:white; -fx-font-weight:bold; -fx-padding:10 20;");
        startBtn.setOnAction(e -> startScrapingTask());

        // ── LAYOUT ───────────────────────────────────────────────────────
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(titleLabel, daysBox, fileBox, startBtn, statusRow, progressBar, logArea);

        Scene scene = new Scene(root, 550, 520);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(event -> {
            stopTimer();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();
    }

    // --- Timer ve Dosya İşlemleri ---
    private void startTimer() {
        startTimeMs.set(System.currentTimeMillis());
        timerFuture = timerScheduler.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - startTimeMs.get();
            long h = elapsed / 3600000; long m = (elapsed % 3600000) / 60000; long s = (elapsed % 60000) / 1000;
            Platform.runLater(() -> timerLabel.setText(String.format("⏱  %02d:%02d:%02d", h, m, s)));
        }, 0, 1, TimeUnit.SECONDS);
    }
    private void stopTimer() { if (timerFuture != null) timerFuture.cancel(false); }
    private void selectSaveLocation(Stage s) {
        FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File f = fc.showSaveDialog(s); if (f != null) { selectedSaveFile = f; pathField.setText(f.getAbsolutePath()); }
    }

    // --- SCRAPING MANTIĞI (Playwright Entegrasyonu) ---
    private void startScrapingTask() {
        if (selectedSaveFile == null) return;

        final int daysToProcess = daysCombo.getValue();
        final String savePath = selectedSaveFile.getAbsolutePath();

        startBtn.setDisable(true);
        doneCount.set(0);
        resultList.clear();
        logArea.clear();
        startTimer();

        new Thread(() -> {
            try {
                AppLogger.log("=== FAZ 1: MAC LİSTESİ TOPLANIYOR ===");
                Platform.runLater(() -> statusLabel.setText("Faz 1: Liste alınıyor..."));

                List<MatchData> pendingMatches;
                // factory AutoCloseable olduğu için burada düzgünce kapanır
                try (PlaywrightFactory factory = new PlaywrightFactory(); Page p = factory.createPage()) {
                    pendingMatches = MatchListScraper.collectMatchesForDays(p, daysToProcess);
                }

                if (pendingMatches.isEmpty()) {
                    AppLogger.log("Hiç maç bulunamadı.");
                    Platform.runLater(() -> { startBtn.setDisable(false); statusLabel.setText("Maç yok."); });
                    stopTimer();
                    return;
                }

                AppLogger.log("\n=== FAZ 2: " + pendingMatches.size() + " MAC PARALEL TARANIYOR ===");
                Platform.runLater(() -> statusLabel.setText("Faz 2: Oranlar çekiliyor..."));

                runParallelScraping(pendingMatches);

                AppLogger.log("\n=== FAZ 3: EXCEL RAPORU OLUSTURULUYOR ===");
                ExcelReportService.generateReport(resultList, savePath);

                AppLogger.log("BİTTİ! Rapor: " + savePath);
                Platform.runLater(() -> { statusLabel.setText("Tamamlandı!"); startBtn.setDisable(false); progressBar.setProgress(1.0); });
                stopTimer();

            } catch (Exception e) {
                AppLogger.log("KRİTİK HATA: " + e.getMessage());
                Platform.runLater(() -> startBtn.setDisable(false));
                stopTimer();
            }
        }).start();
    }

    private void runParallelScraping(List<MatchData> matches) {
        int total = matches.size();
        int threads = ScraperConstants.MAX_CONCURRENT_DRIVERS;
        Semaphore sem = new Semaphore(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (MatchData m : matches) {
            executor.submit(() -> {
                sem.acquireUninterruptibly();
                try (PlaywrightFactory factory = new PlaywrightFactory(); Page page = factory.createPage()) {
                    MatchDetailScraper.scrapeMatch(page, m);
                    resultList.add(m);
                    int done = doneCount.incrementAndGet();
                    AppLogger.log(String.format("  [OK %d/%d] %s vs %s", done, total, m.homeTeam, m.awayTeam));
                    Platform.runLater(() -> progressBar.setProgress((double) done / total));
                } catch (Exception e) {
                    AppLogger.log("  [ERR] " + m.homeTeam);
                } finally {
                    sem.release();
                }
            });
        }
        executor.shutdown();
        try { executor.awaitTermination(2, TimeUnit.HOURS); } catch (Exception ignored) {}
    }
}