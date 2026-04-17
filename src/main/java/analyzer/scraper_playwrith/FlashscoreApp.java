package analyzer.scraper_playwrith;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
        primaryStage.setTitle("FlashScore Bet365 Enterprise v3.1 - Pool Edition");

        Label titleLabel = new Label("FlashScore Bet365 Bot");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        HBox daysBox = new HBox(10);
        daysBox.setAlignment(Pos.CENTER_LEFT);
        daysCombo = new ComboBox<>();
        daysCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7);
        daysCombo.setValue(1);
        daysBox.getChildren().addAll(new Label("Taranacak Gün Sayısı:"), daysCombo);

        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        pathField = new TextField();
        pathField.setEditable(false);
        pathField.setPrefWidth(280);
        Button browseBtn = new Button("Gözat...");
        browseBtn.setOnAction(e -> selectSaveLocation(primaryStage));
        fileBox.getChildren().addAll(new Label("Kaydedilecek Yer:"), pathField, browseBtn);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(420);
        progressBar.setPrefHeight(22);
        statusLabel = new Label("Bekleniyor...");
        timerLabel = new Label("⏱ 00:00:00");
        timerLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px; -fx-text-fill: #7f8c8d;");

        HBox statusRow = new HBox(20);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getChildren().addAll(statusLabel, timerLabel);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(220);
        logArea.setStyle("-fx-control-inner-background:#1e1e1e; -fx-text-fill:#00ff00; -fx-font-family:'Consolas';");
        AppLogger.setConsoleArea(logArea);

        startBtn = new Button("TARAMAYI BAŞLAT");
        startBtn.setStyle("-fx-background-color:#27ae60; -fx-text-fill:white; -fx-font-weight:bold; -fx-padding:12 25;");
        startBtn.setOnAction(e -> startScrapingTask());

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(titleLabel, daysBox, fileBox, startBtn, statusRow, progressBar, logArea);

        Scene scene = new Scene(root, 580, 550);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(e -> {
            stopTimer();
            BrowserPool.getInstance().close();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();
    }

    private void startTimer() {
        startTimeMs.set(System.currentTimeMillis());
        timerFuture = timerScheduler.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - startTimeMs.get();
            long h = elapsed / 3600000;
            long m = (elapsed % 3600000) / 60000;
            long s = (elapsed % 60000) / 1000;
            Platform.runLater(() -> timerLabel.setText(String.format("⏱ %02d:%02d:%02d", h, m, s)));
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void stopTimer() {
        if (timerFuture != null) timerFuture.cancel(false);
    }

    private void selectSaveLocation(Stage s) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File f = fc.showSaveDialog(s);
        if (f != null) {
            selectedSaveFile = f;
            pathField.setText(f.getAbsolutePath());
        }
    }

    private void startScrapingTask() {
        if (selectedSaveFile == null) {
            AppLogger.log("Lütfen önce kaydetme konumu seçin!");
            return;
        }

        final int daysToProcess = daysCombo.getValue();
        final String savePath = selectedSaveFile.getAbsolutePath();

        startBtn.setDisable(true);
        doneCount.set(0);
        resultList.clear();
        logArea.clear();
        startTimer();

        new Thread(() -> {
            try {
                AppLogger.log("=== FAZ 1: MAÇ LİSTESİ TOPLANIYOR ===");
                Platform.runLater(() -> statusLabel.setText("Faz 1: Liste alınıyor..."));

                List<MatchData> pendingMatches;
                try (Playwright playwright = Playwright.create();
                     Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                     BrowserContext ctx = browser.newContext();
                     Page page = ctx.newPage()) {

                    pendingMatches = MatchListScraper.collectMatchesForDays(page, daysToProcess);
                }

                if (pendingMatches.isEmpty()) {
                    AppLogger.log("Hiç maç bulunamadı.");
                    Platform.runLater(() -> {
                        startBtn.setDisable(false);
                        statusLabel.setText("Maç bulunamadı.");
                    });
                    stopTimer();
                    return;
                }

                AppLogger.log("Bulunan maç: " + pendingMatches.size());
                AppLogger.log("\n=== FAZ 2: PARALEL TARAMA BAŞLIYOR ===");
                Platform.runLater(() -> statusLabel.setText("Faz 2: Oranlar çekiliyor..."));

                runParallelScraping(pendingMatches);

                AppLogger.log("\n=== FAZ 3: EXCEL RAPORU OLUŞTURULUYOR ===");
                ExcelReportService.generateReport(resultList, savePath);

                AppLogger.log("İŞLEM TAMAMLANDI! → " + savePath);
                Platform.runLater(() -> {
                    statusLabel.setText("Tamamlandı!");
                    startBtn.setDisable(false);
                    progressBar.setProgress(1.0);
                });
                stopTimer();

            } catch (Exception e) {
                AppLogger.log("KRİTİK HATA: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> startBtn.setDisable(false));
                stopTimer();
            }
        }).start();
    }

    private void runParallelScraping(List<MatchData> matches) {
        int total = matches.size();
        int maxThreads = ScraperConstants.MAX_CONCURRENT_DRIVERS;

        ExecutorService executor = Executors.newFixedThreadPool(maxThreads, new PlaywrightThreadFactory());

        for (MatchData m : matches) {
            executor.submit(() -> {
                Playwright playwright = null;
                Browser browser = null;
                BrowserContext context = null;
                Page page = null;

                try {
                    playwright = PlaywrightThreadFactory.createPlaywright();
                    browser = PlaywrightThreadFactory.createBrowser(playwright);
                    context = PlaywrightThreadFactory.createContext(browser);
                    page = context.newPage();

                    MatchDetailScraper.scrapeMatch(page, m);

                    resultList.add(m);
                    int done = doneCount.incrementAndGet();

                    AppLogger.log(String.format(" [OK %d/%d] %s vs %s", done, total, m.homeTeam, m.awayTeam));
                    Platform.runLater(() -> progressBar.setProgress((double) done / total));

                } catch (Exception e) {
                    AppLogger.log(" [ERR] " + m.homeTeam + " -> " + e.getMessage());
                } finally {
                    try {
                        if (page != null) page.close();
                        if (context != null) context.close();
                        if (browser != null) browser.close();
                        if (playwright != null) playwright.close();
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(4, TimeUnit.HOURS);
        } catch (Exception ignored) {
        }
    }
}