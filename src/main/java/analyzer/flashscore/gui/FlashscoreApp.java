package analyzer.flashscore.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FlashscoreApp extends Application {

    private final CopyOnWriteArrayList<MatchData> resultList = new CopyOnWriteArrayList<>();
    private final AtomicInteger doneCount   = new AtomicInteger(0);
    private final AtomicLong    startTimeMs = new AtomicLong(0);

    // Timer altyapısı
    private final ScheduledExecutorService timerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "elapsed-timer");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> timerFuture;

    // UI bileşenleri
    private ComboBox<Integer> daysCombo;
    private TextField         pathField;
    private Button            startBtn;
    private ProgressBar       progressBar;
    private Label             statusLabel;
    private Label             timerLabel;   // ← YENİ: keçən vaxt
    private TextArea          logArea;
    private File              selectedSaveFile;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Bet365 Enterprise Scraper v2.0");

        // ── Başlık ──────────────────────────────────────────────────────
        Label titleLabel = new Label("FlashScore Bet365 Bot");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // ── Gün seçimi ──────────────────────────────────────────────────
        HBox daysBox = new HBox(10);
        daysBox.setAlignment(Pos.CENTER_LEFT);
        daysCombo = new ComboBox<>();
        daysCombo.getItems().addAll(1,2,3,4,5,6,7);
        daysCombo.setValue(1);
        daysBox.getChildren().addAll(new Label("Taranacak Gün Sayısı:"), daysCombo);

        // ── Fayl seçimi ─────────────────────────────────────────────────
        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        pathField = new TextField();
        pathField.setEditable(false);
        pathField.setPrefWidth(250);
        Button browseBtn = new Button("Gözat...");
        browseBtn.setOnAction(e -> selectSaveLocation(primaryStage));
        fileBox.getChildren().addAll(new Label("Kaydedilecek Yer:"), pathField, browseBtn);

        // ── Progress + Status + Timer ────────────────────────────────────
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setPrefHeight(20);

        statusLabel = new Label("Bekleniyor...");

        // Vaxt sayğacı — sağa hizalanmış, mono font
        timerLabel = new Label("⏱  00:00:00");
        timerLabel.setStyle(
                "-fx-font-family: 'Consolas', monospace;" +
                "-fx-font-size: 13px;" +
                "-fx-text-fill: #7f8c8d;"
        );

        // Status + timer yan yana
        HBox statusRow = new HBox();
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setSpacing(20);
        statusRow.getChildren().addAll(statusLabel, timerLabel);

        // ── Log sahəsi ───────────────────────────────────────────────────
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        logArea.setStyle("-fx-control-inner-background:#1e1e1e;-fx-text-fill:#00ff00;-fx-font-family:'Consolas';");
        AppLogger.setConsoleArea(logArea);

        // ── Başlat düyməsi ───────────────────────────────────────────────
        startBtn = new Button("TARAMAYI BAŞLAT");
        startBtn.setStyle("-fx-background-color:#27ae60;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:10 20;");
        startBtn.setOnAction(e -> startScrapingTask());

        // ── Layout ───────────────────────────────────────────────────────
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(titleLabel, daysBox, fileBox, startBtn, statusRow, progressBar, logArea);

        Scene scene = new Scene(root, 550, 520);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(event -> {
            stopTimer();
            forceCleanupDrivers();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();
    }

    // ====================================================================
    // Timer köməkçi metodlar
    // ====================================================================

    private void startTimer() {
        startTimeMs.set(System.currentTimeMillis());
        // Hər saniyə UI-ı güncəllə
        timerFuture = timerScheduler.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - startTimeMs.get();
            long hours   = elapsed / 3_600_000;
            long minutes = (elapsed % 3_600_000) / 60_000;
            long seconds = (elapsed % 60_000) / 1_000;
            String text  = String.format("⏱  %02d:%02d:%02d", hours, minutes, seconds);
            Platform.runLater(() -> timerLabel.setText(text));
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void stopTimer() {
        if (timerFuture != null && !timerFuture.isCancelled()) {
            timerFuture.cancel(false);
        }
    }

    /** Timeri durdur, son keçən vaxtı hesabla, label-i rəngləndir */
    private void finalizeTimer() {
        stopTimer();
        long elapsed = System.currentTimeMillis() - startTimeMs.get();
        long hours   = elapsed / 3_600_000;
        long minutes = (elapsed % 3_600_000) / 60_000;
        long seconds = (elapsed % 60_000) / 1_000;
        String finalText = String.format("⏱  %02d:%02d:%02d  (tamamlandi)", hours, minutes, seconds);
        Platform.runLater(() -> {
            timerLabel.setText(finalText);
            timerLabel.setStyle(
                    "-fx-font-family:'Consolas',monospace;" +
                    "-fx-font-size:13px;" +
                    "-fx-text-fill:#27ae60;" +   // yaşıl — tamamlandı
                    "-fx-font-weight:bold;"
            );
        });
    }

    // ====================================================================
    // Fayl seçimi
    // ====================================================================

    private void selectSaveLocation(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Excel Dosyasını Kaydet");
        fc.setInitialFileName("bet365.xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Dosyası","*.xlsx"));
        File file = fc.showSaveDialog(stage);
        if (file != null) { selectedSaveFile = file; pathField.setText(file.getAbsolutePath()); }
    }

    // ====================================================================
    // Scraping başlat
    // ====================================================================

    private void startScrapingTask() {
        if (selectedSaveFile == null) {
            showAlert("Hata","Lütfen excel dosyasının kaydedileceği yeri seçin!");
            return;
        }

        startBtn.setDisable(true);
        daysCombo.setDisable(true);
        updateProgress(0);
        logArea.clear();
        resultList.clear();
        doneCount.set(0);

        // Timer sıfırla ve başlat
        Platform.runLater(() -> timerLabel.setStyle(
                "-fx-font-family:'Consolas',monospace;-fx-font-size:13px;-fx-text-fill:#7f8c8d;"));
        startTimer();

        int days     = daysCombo.getValue();
        String savePath = selectedSaveFile.getAbsolutePath();

        Thread scraperThread = new Thread(() -> {
            try {
                AppLogger.log("=== FAZ 1: MAC ID'LERI TOPLANIYOR ===");
                updateStatus("Faz 1: Maç listesi çıkarılıyor...");
                updateProgress(-1);

                WebDriver listDriver = DriverFactory.createHeadlessDriver();
                List<MatchData> pendingMatches;
                try {
                    pendingMatches = MatchListScraper.collectMatchesForDays(listDriver, days);
                } finally {
                    listDriver.quit();
                }

                if (pendingMatches.isEmpty()) {
                    AppLogger.log("Hiç maç bulunamadı.");
                    finalizeTimer();
                    finishTask("Maç bulunamadı.");
                    return;
                }

                AppLogger.log("\n=== FAZ 2: " + pendingMatches.size() + " MAC PARALEL OLARAK TARANIYOR ===");
                updateStatus("Faz 2: Oranlar çekiliyor (Paralel)...");
                updateProgress(0);

                runParallelScraping(pendingMatches);

                AppLogger.log("\n=== FAZ 3: EXCEL RAPORU OLUSTURULUYOR ===");
                updateStatus("Faz 3: Excel dosyası yazılıyor...");
                updateProgress(-1);

                ExcelReportService.generateReport(resultList, savePath);
                AppLogger.log("Tamamlandi! Fayl: " + savePath);
                forceCleanupDrivers();

                finalizeTimer();
                finishTask("Tamamlandı! " + resultList.size() + " maç yazıldı.");

            } catch (Exception e) {
                AppLogger.log("KRİTİK HATA: " + e.getMessage());
                forceCleanupDrivers();
                finalizeTimer();
                finishTask("Hata oluştu!");
            }
        });

        scraperThread.setDaemon(true);
        scraperThread.start();
    }

    // ====================================================================
    // Paralel scraping
    // ====================================================================

    private void runParallelScraping(List<MatchData> matches) throws InterruptedException {
        int total     = matches.size();
        Semaphore sem = new Semaphore(ScraperConstants.MAX_CONCURRENT_DRIVERS, true);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (MatchData match : matches) {
                executor.submit(() -> {
                    sem.acquireUninterruptibly();
                    WebDriver driver = null;
                    try {
                        // YENİ EKLENDİ: Her thread başlamadan önce 1 ile 3.5 saniye arası rastgele beklesin.
                        // Bu sayede 4 browser aynı milisaniyede siteye saldırmaz, IP engeli yemezsiniz.
                        Thread.sleep((long) (Math.random() * 2500 + 1000));

                        driver = DriverFactory.createHeadlessDriver();
                        driver.get(ScraperConstants.BASE_URL);
                        WaitActionUtils.smartClick(driver, By.id("onetrust-accept-btn-handler"), 3);
                        OddsConfigurator.configureDecimalOdds(driver);

                        MatchDetailScraper.scrapeMatch(driver, match);
                        resultList.add(match);

                        int done = doneCount.incrementAndGet();
                        AppLogger.log(String.format("  [OK %d/%d] %s vs %s", done, total, match.homeTeam, match.awayTeam));
                        updateProgress((double) done / total);

                    } catch (Exception e) {
                        int done = doneCount.incrementAndGet();
                        AppLogger.log(String.format("  [ERR] %s - %s", match.homeTeam, e.getMessage()));
                        updateProgress((double) done / total);
                    } finally {
                        if (driver != null) driver.quit();
                        sem.release();
                    }
                });
            }
        }
    }

    // ====================================================================
    // UI güncəlləmə köməkçiləri
    // ====================================================================

    private void updateStatus(String text)    { Platform.runLater(() -> statusLabel.setText(text)); }
    private void updateProgress(double value) { Platform.runLater(() -> progressBar.setProgress(value)); }

    private void finishTask(String finalStatus) {
        Platform.runLater(() -> {
            statusLabel.setText(finalStatus);
            progressBar.setProgress(1.0);
            startBtn.setDisable(false);
            daysCombo.setDisable(false);
        });
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void forceCleanupDrivers() {
        try {
            AppLogger.log("Driver temizligi baslatildi...");
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win"))
                Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe /T");
            else
                Runtime.getRuntime().exec("pkill -f chromedriver");
            AppLogger.log("Temizlik tamamlandi.");
        } catch (Exception e) {
            AppLogger.log("Temizlik hatasi: " + e.getMessage());
        }
    }
}