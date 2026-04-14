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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class FlashscoreApp extends Application {

    private final CopyOnWriteArrayList<MatchData> resultList = new CopyOnWriteArrayList<>();
    private final AtomicInteger doneCount = new AtomicInteger(0);

    private ComboBox<Integer> daysCombo;
    private TextField pathField;
    private Button startBtn;
    private ProgressBar progressBar;
    private Label statusLabel;
    private TextArea logArea;
    private File selectedSaveFile;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Bet365 Enterprise Scraper v2.0");

        Label titleLabel = new Label("FlashScore Bet365 Bot");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        HBox daysBox = new HBox(10);
        daysBox.setAlignment(Pos.CENTER_LEFT);
        Label daysLabel = new Label("Taranacak Gün Sayısı:");
        daysCombo = new ComboBox<>();
        daysCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7);
        daysCombo.setValue(1);
        daysBox.getChildren().addAll(daysLabel, daysCombo);

        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        Label pathLabel = new Label("Kaydedilecek Yer:");
        pathField = new TextField();
        pathField.setEditable(false);
        pathField.setPrefWidth(250);
        Button browseBtn = new Button("Gözat...");
        browseBtn.setOnAction(e -> selectSaveLocation(primaryStage));
        fileBox.getChildren().addAll(pathLabel, pathField, browseBtn);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setPrefHeight(20);
        statusLabel = new Label("Bekleniyor...");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        logArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #00ff00; -fx-font-family: 'Consolas';");
        AppLogger.setConsoleArea(logArea);

        startBtn = new Button("TARAMAYI BAŞLAT");
        startBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        startBtn.setOnAction(e -> startScrapingTask());

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(titleLabel, daysBox, fileBox, startBtn, statusLabel, progressBar, logArea);

        Scene scene = new Scene(root, 550, 500);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(event -> {
            forceCleanupDrivers();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();
    }

    private void selectSaveLocation(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Excel Dosyasını Kaydet");
        fileChooser.setInitialFileName("bet365.xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Dosyası", "*.xlsx"));

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            selectedSaveFile = file;
            pathField.setText(file.getAbsolutePath());
        }
    }

    private void startScrapingTask() {
        if (selectedSaveFile == null) {
            showAlert("Hata", "Lütfen excel dosyasının kaydedileceği yeri seçin!");
            return;
        }

        startBtn.setDisable(true);
        daysCombo.setDisable(true);
        updateProgress(0);
        logArea.clear();
        resultList.clear();
        doneCount.set(0);

        int days = daysCombo.getValue();
        String savePath = selectedSaveFile.getAbsolutePath();

        Thread scraperThread = new Thread(() -> {
            try {
                AppLogger.log("=== FAZ 1: MAC ID'LERI TOPLANIYOR ===");
                updateStatus("Faz 1: Maç listesi çıkarılıyor...");
                updateProgress(-1); // Belirsiz yüklenme animasyonu

                WebDriver listDriver = DriverFactory.createHeadlessDriver();
                List<MatchData> pendingMatches;
                try {
                    pendingMatches = MatchListScraper.collectMatchesForDays(listDriver, days);
                } finally {
                    listDriver.quit();
                }

                if (pendingMatches.isEmpty()) {
                    AppLogger.log("Hiç maç bulunamadı. İşlem iptal ediliyor.");
                    finishTask("Maç bulunamadı.");
                    return;
                }

                AppLogger.log("\n=== FAZ 2: " + pendingMatches.size() + " MAC PARALEL OLARAK TARANIYOR ===");
                updateStatus("Faz 2: Oranlar çekiliyor (Paralel)...");
                updateProgress(0);

                runParallelScraping(pendingMatches);

                AppLogger.log("\n=== FAZ 3: EXCEL RAPORU OLUSTURULUYOR ===");
                updateStatus("Faz 3: Excel dosyası diske yazılıyor...");
                updateProgress(-1);

                ExcelReportService.generateReport(resultList, savePath);

                AppLogger.log("İşlem başarıyla tamamlandı! Dosya: " + savePath);
                forceCleanupDrivers();
                finishTask("Tamamlandı!");

            } catch (Exception e) {
                AppLogger.log("KRİTİK HATA: " + e.getMessage());
                forceCleanupDrivers();
                finishTask("Hata oluştu!");
            }
        });

        scraperThread.setDaemon(true);
        scraperThread.start();
    }

    private void runParallelScraping(List<MatchData> matches) throws InterruptedException {
        int total = matches.size();
        Semaphore semaphore = new Semaphore(ScraperConstants.MAX_CONCURRENT_DRIVERS, true);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (MatchData match : matches) {
                executor.submit(() -> {
                    semaphore.acquireUninterruptibly();
                    WebDriver driver = null;
                    try {
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
                        semaphore.release();
                    }
                });
            }
        }
    }

    // --- GÜVENLİ UI GÜNCELLEME METODLARI ---
    private void updateStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    private void updateProgress(double value) {
        Platform.runLater(() -> progressBar.setProgress(value));
    }

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
            AppLogger.log("Sistem kontrol ediliyor... Kapanmayan Driver'lar temizleniyor.");
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows için TaskKill komutu (/F zorla kapatır, /IM imaj adını seçer)
                Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe /T");
            } else if (os.contains("mac") || os.contains("nix") || os.contains("nux")) {
                // Mac veya Linux için Kill komutu
                Runtime.getRuntime().exec("pkill -f chromedriver");
            }
            AppLogger.log("Arka plan RAM temizliği başarıyla tamamlandı!");
        } catch (Exception e) {
            AppLogger.log("Temizlik sırasında bir hata oluştu: " + e.getMessage());
        }
    }
}