package flashscore.weeklydatascraping;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class MackolikPatternDiscovery {

    private WebDriver driver;
    private WebDriverWait wait;

    static class Match {
        String date;
        String homeTeam;
        String awayTeam;
        String htScore;
        String ftScore;
        int week;

        int getHTHome() { return htScore == null ? -1 : Integer.parseInt(htScore.split("-")[0].trim()); }
        int getHTAway() { return htScore == null ? -1 : Integer.parseInt(htScore.split("-")[1].trim()); }
        int getFTHome() { return ftScore == null ? -1 : Integer.parseInt(ftScore.split("-")[0].trim()); }
        int getFTAway() { return ftScore == null ? -1 : Integer.parseInt(ftScore.split("-")[1].trim()); }

        String getHTFTResult() {
            if (htScore == null || ftScore == null) return "Unknown";
            int htH = getHTHome(), htA = getHTAway(), ftH = getFTHome(), ftA = getFTAway();
            String htR = htH > htA ? "1" : (htH < htA ? "2" : "0");
            String ftR = ftH > ftA ? "1" : (ftH < ftA ? "2" : "0");
            return htR + "/" + ftR;
        }

        @Override
        public String toString() {
            return String.format("Hafta %d | %s vs %s | HT:%s FT:%s | 🎯%s",
                    week, homeTeam, awayTeam, htScore, ftScore, getHTFTResult());
        }
    }

    static class DiscoveredPattern {
        String type;
        String triggerValue;
        int gapMatches;
        String resultHTFT;
        int foundCount;
        int totalChecked;
        double accuracy;
        List<String> examples;

        DiscoveredPattern(String type, String trigger, int gap, String result) {
            this.type = type;
            this.triggerValue = trigger;
            this.gapMatches = gap;
            this.resultHTFT = result;
            this.examples = new ArrayList<>();
        }

        void calculate() {
            this.accuracy = totalChecked > 0 ? (double) foundCount / totalChecked * 100 : 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n" + "💎".repeat(50) + "\n");
            sb.append("🔥 KEŞFEDİLEN PATTERN!\n");
            sb.append("📌 Tip: ").append(type).append("\n");
            sb.append("🎯 Trigger: ").append(triggerValue).append("\n");
            sb.append("⏳ Gap: ").append(gapMatches).append(" maç sonra\n");
            sb.append("✅ Sonuç: ").append(resultHTFT).append("\n");
            sb.append("📊 Başarı: ").append(foundCount).append("/").append(totalChecked)
                    .append(" = ").append(String.format("%.1f%%", accuracy)).append("\n");

            if (!examples.isEmpty()) {
                sb.append("\n🎲 ÖRNEKLER:\n");
                examples.stream().limit(3).forEach(ex -> sb.append("   ").append(ex).append("\n"));
            }

            sb.append("💎".repeat(50) + "\n");
            return sb.toString();
        }
    }

    public MackolikPatternDiscovery() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public List<Match> scrapeMultipleSeasons(String leagueUrl, List<String> seasonIds, int maxWeeksPerSeason) {
        List<Match> allMatches = new ArrayList<>();

        for (String seasonId : seasonIds) {
            System.out.println("\n" + "🔥".repeat(40));
            System.out.println("🎯 SEZON: " + seasonId + " YÜKLENİYOR...");
            System.out.println("🔥".repeat(40) + "\n");

            try {
                driver.get(leagueUrl);
                Thread.sleep(3000);

                // Sezon seç
                Select seasonSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cboSeason"))));
                seasonSelect.selectByValue(seasonId);
                Thread.sleep(3000);

                // Fikstür sekmesine tıkla
                WebElement fiksturLink = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Fikstür")));
                fiksturLink.click();
                Thread.sleep(3000);

                // İLK HAFTAYA GİT
                WebElement firstButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("span.first")));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", firstButton);
                Thread.sleep(2000);
                System.out.println("✅ İlk haftaya gidildi!\n");

                int weekCounter = 0;
                int seasonMatchCount = 0;

                // NEXT butonu ile tüm haftalarda gez
                while (weekCounter < maxWeeksPerSeason) {
                    weekCounter++;

                    // Mevcut haftayı oku
                    Select weekSelect = new Select(driver.findElement(By.id("cboWeek")));
                    String currentWeek = weekSelect.getFirstSelectedOption().getText();
                    System.out.println("📅 " + currentWeek + " işleniyor...");

                    // Tabloyu bekle ve oku
                    WebElement table = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#dvFixtureInner table.list-table")));
                    List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));

                    int matchesInWeek = 0;

                    for (WebElement row : rows) {
                        try {
                            // Header satırını atla
                            if (row.getAttribute("class") != null && row.getAttribute("class").contains("table-header")) {
                                continue;
                            }

                            List<WebElement> cells = row.findElements(By.tagName("td"));
                            if (cells.size() < 9) continue;

                            Match match = new Match();
                            match.week = weekCounter;
                            match.date = cells.get(0).getText().trim();
                            match.homeTeam = cells.get(3).getText().trim();
                            match.awayTeam = cells.get(7).getText().trim();

                            String ftScore = cells.get(5).getText().trim();
                            String htScore = cells.get(8).getText().trim();

                            if (!ftScore.isEmpty() && !htScore.isEmpty() &&
                                    ftScore.contains("-") && htScore.contains("-") &&
                                    !ftScore.equals("-") && !htScore.equals("-")) {
                                match.ftScore = ftScore;
                                match.htScore = htScore;
                                allMatches.add(match);
                                matchesInWeek++;
                                seasonMatchCount++;
                            }

                        } catch (Exception e) {
                            // Satır parse hatası - devam et
                        }
                    }

                    System.out.println("   ✅ " + matchesInWeek + " maç (Sezon: " + seasonMatchCount + " | Toplam: " + allMatches.size() + ")");

                    // Eğer bu haftada maç yoksa (henüz oynanmadı), dur
                    if (matchesInWeek == 0) {
                        System.out.println("   ⚠️ Bu hafta henüz oynanmamış, bir sonraki sezona geçiliyor!");
                        break;
                    }

                    // Sonraki haftaya geç
                    try {
                        WebElement nextButton = driver.findElement(By.cssSelector("span.next"));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButton);
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        System.out.println("   ✅ Sezon sonu! Toplam " + seasonMatchCount + " maç eklendi.");
                        break;
                    }
                }

                System.out.println("\n✅ SEZON " + seasonId + " TAMAMLANDI: " + seasonMatchCount + " maç\n");

            } catch (Exception e) {
                System.out.println("❌ Sezon " + seasonId + " hatası: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("\n🏆🏆🏆 TÜM SEZONLAR TAMAMLANDI! 🏆🏆🏆");
        System.out.println("📊 TOPLAM " + allMatches.size() + " MAÇ YÜKLENDİ!\n");

        return allMatches;
    }

    private Map<String, List<Match>> groupByTeam(List<Match> matches) {
        Map<String, List<Match>> map = new HashMap<>();

        for (Match m : matches) {
            map.putIfAbsent(m.homeTeam, new ArrayList<>());
            map.putIfAbsent(m.awayTeam, new ArrayList<>());
            map.get(m.homeTeam).add(m);
            map.get(m.awayTeam).add(m);
        }

        for (var entry : map.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(m -> m.week));
        }

        return map;
    }

    // 🔥 DİNAMİK PATTERN KEŞFİ - TAKIM BAZLI
    public List<DiscoveredPattern> discoverTeamPatterns(List<Match> matches, int minAccuracy) {
        System.out.println("🔍 TAKIM BAZLI PATTERN'LER ARANIYOR...\n");

        List<DiscoveredPattern> discovered = new ArrayList<>();
        Map<String, List<Match>> teamMatches = groupByTeam(matches);

        // Tüm takımları al
        Set<String> allTeams = new HashSet<>();
        matches.forEach(m -> {
            allTeams.add(m.homeTeam);
            allTeams.add(m.awayTeam);
        });

        // Her takım için test et
        for (String triggerTeam : allTeams) {
            System.out.println("   🎯 Testing: " + triggerTeam);

            // 1-5 maç gap test et
            for (int gap = 1; gap <= 5; gap++) {

                // 2/1 ve 1/2 için test et
                for (String targetHTFT : Arrays.asList("2/1", "1/2")) {

                    DiscoveredPattern pattern = new DiscoveredPattern(
                            "TAKIM TRIGGER", triggerTeam, gap, targetHTFT);

                    // Her takımın maçlarını kontrol et
                    for (var entry : teamMatches.entrySet()) {
                        List<Match> teamList = entry.getValue();

                        for (int i = 0; i < teamList.size(); i++) {
                            Match current = teamList.get(i);

                            // Bu takım trigger takımıyla mı oynadı?
                            if (current.homeTeam.equals(triggerTeam) || current.awayTeam.equals(triggerTeam)) {

                                if (i + gap < teamList.size()) {
                                    Match resultMatch = teamList.get(i + gap);
                                    pattern.totalChecked++;

                                    if (resultMatch.getHTFTResult().equals(targetHTFT)) {
                                        pattern.foundCount++;
                                        pattern.examples.add(current + " → " + resultMatch);
                                    }
                                }
                            }
                        }
                    }

                    pattern.calculate();

                    // Daha esnek kriterler: min 5 test ve %40 başarı
                    if (pattern.accuracy >= minAccuracy && pattern.totalChecked >= 5) {
                        discovered.add(pattern);
                    }
                }
            }
        }

        System.out.println("✅ " + discovered.size() + " takım pattern bulundu!\n");
        return discovered;
    }

    // 🔥 YENİ! CORRECT SCORE PATTERN KEŞFİ
    public List<DiscoveredPattern> discoverCorrectScorePatterns(List<Match> matches, int minAccuracy) {
        System.out.println("🔍 CORRECT SCORE BAZLI PATTERN'LER ARANIYOR...\n");

        List<DiscoveredPattern> discovered = new ArrayList<>();
        Map<String, List<Match>> teamMatches = groupByTeam(matches);

        // En popüler skorları bul
        Map<String, Integer> scoreFrequency = new HashMap<>();
        matches.forEach(m -> {
            if (m.ftScore != null) {
                scoreFrequency.put(m.ftScore, scoreFrequency.getOrDefault(m.ftScore, 0) + 1);
            }
        });

        // En çok görülen 15 skoru al
        List<String> popularScores = scoreFrequency.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(15)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        System.out.println("   📊 En popüler 15 skor: " + String.join(", ", popularScores));

        // Her popüler skor için pattern ara
        for (String triggerScore : popularScores) {
            for (int gap = 1; gap <= 3; gap++) {

                // Bu skordan N maç sonra hangi SKOR gelir?
                DiscoveredPattern scoreToScorePattern = new DiscoveredPattern(
                        "CORRECT SCORE → SCORE", triggerScore + " → " + gap + " maç sonra", gap, "SCORE");

                Map<String, Integer> nextScores = new HashMap<>();

                for (var entry : teamMatches.entrySet()) {
                    List<Match> teamList = entry.getValue();

                    for (int i = 0; i < teamList.size(); i++) {
                        Match current = teamList.get(i);

                        if (triggerScore.equals(current.ftScore)) {

                            if (i + gap < teamList.size()) {
                                Match resultMatch = teamList.get(i + gap);
                                scoreToScorePattern.totalChecked++;

                                String nextScore = resultMatch.ftScore;
                                nextScores.put(nextScore, nextScores.getOrDefault(nextScore, 0) + 1);
                            }
                        }
                    }
                }

                // En çok tekrar eden skoru bul
                if (!nextScores.isEmpty()) {
                    var mostCommon = nextScores.entrySet().stream()
                            .max((a, b) -> a.getValue().compareTo(b.getValue()))
                            .get();

                    scoreToScorePattern.foundCount = mostCommon.getValue();
                    scoreToScorePattern.resultHTFT = mostCommon.getKey();
                    scoreToScorePattern.calculate();

                    if (scoreToScorePattern.accuracy >= minAccuracy && scoreToScorePattern.totalChecked >= 5) {
                        discovered.add(scoreToScorePattern);
                    }
                }

                // Bu skordan N maç sonra hangi HTFT gelir?
                for (String targetHTFT : Arrays.asList("2/1", "1/2", "1/0", "2/0")) {
                    DiscoveredPattern scoreToHTFTPattern = new DiscoveredPattern(
                            "CORRECT SCORE → HTFT", triggerScore, gap, targetHTFT);

                    for (var entry : teamMatches.entrySet()) {
                        List<Match> teamList = entry.getValue();

                        for (int i = 0; i < teamList.size(); i++) {
                            Match current = teamList.get(i);

                            if (triggerScore.equals(current.ftScore)) {

                                if (i + gap < teamList.size()) {
                                    Match resultMatch = teamList.get(i + gap);
                                    scoreToHTFTPattern.totalChecked++;

                                    if (resultMatch.getHTFTResult().equals(targetHTFT)) {
                                        scoreToHTFTPattern.foundCount++;
                                        scoreToHTFTPattern.examples.add(current + " → " + resultMatch);
                                    }
                                }
                            }
                        }
                    }

                    scoreToHTFTPattern.calculate();

                    if (scoreToHTFTPattern.accuracy >= minAccuracy && scoreToHTFTPattern.totalChecked >= 5) {
                        discovered.add(scoreToHTFTPattern);
                    }
                }
            }
        }

        System.out.println("✅ " + discovered.size() + " correct score pattern bulundu!\n");
        return discovered;
    }

    // 🔥 DİNAMİK PATTERN KEŞFİ - SKOR BAZLI
    public List<DiscoveredPattern> discoverScorePatterns(List<Match> matches, int minAccuracy) {
        System.out.println("🔍 SKOR BAZLI PATTERN'LER ARANIYOR...\n");

        List<DiscoveredPattern> discovered = new ArrayList<>();
        Map<String, List<Match>> teamMatches = groupByTeam(matches);

        // Tüm skorları topla
        Set<String> allScores = new HashSet<>();
        matches.forEach(m -> {
            allScores.add(m.htScore);
            allScores.add(m.ftScore);
        });

        System.out.println("   📊 " + allScores.size() + " farklı skor bulundu");

        // Her skor için test et
        for (String triggerScore : allScores) {
            if (triggerScore == null) continue;

            for (int gap = 1; gap <= 5; gap++) {
                for (String targetHTFT : Arrays.asList("2/1", "1/2", "1/0", "2/0")) {

                    DiscoveredPattern pattern = new DiscoveredPattern(
                            "SKOR TRIGGER", triggerScore, gap, targetHTFT);

                    for (var entry : teamMatches.entrySet()) {
                        List<Match> teamList = entry.getValue();

                        for (int i = 0; i < teamList.size(); i++) {
                            Match current = teamList.get(i);

                            if (triggerScore.equals(current.htScore) || triggerScore.equals(current.ftScore)) {

                                if (i + gap < teamList.size()) {
                                    Match resultMatch = teamList.get(i + gap);
                                    pattern.totalChecked++;

                                    if (resultMatch.getHTFTResult().equals(targetHTFT)) {
                                        pattern.foundCount++;
                                        pattern.examples.add(current + " → " + resultMatch);
                                    }
                                }
                            }
                        }
                    }

                    pattern.calculate();

                    if (pattern.accuracy >= minAccuracy && pattern.totalChecked >= 3) {
                        discovered.add(pattern);
                    }
                }
            }
        }

        System.out.println("✅ " + discovered.size() + " skor pattern bulundu!\n");
        return discovered;
    }

    // 🔥 DİNAMİK PATTERN KEŞFİ - HTFT BAZLI
    public List<DiscoveredPattern> discoverHTFTPatterns(List<Match> matches, int minAccuracy) {
        System.out.println("🔍 HTFT BAZLI PATTERN'LER ARANIYOR...\n");

        List<DiscoveredPattern> discovered = new ArrayList<>();
        Map<String, List<Match>> teamMatches = groupByTeam(matches);

        String[] allHTFT = {"1/1", "1/0", "1/2", "0/1", "0/0", "0/2", "2/1", "2/0", "2/2"};

        for (String triggerHTFT : allHTFT) {
            for (int gap = 1; gap <= 5; gap++) {
                for (String targetHTFT : Arrays.asList("2/1", "1/2", "1/0", "2/0")) {

                    DiscoveredPattern pattern = new DiscoveredPattern(
                            "HTFT TRIGGER", triggerHTFT, gap, targetHTFT);

                    for (var entry : teamMatches.entrySet()) {
                        List<Match> teamList = entry.getValue();

                        for (int i = 0; i < teamList.size(); i++) {
                            Match current = teamList.get(i);

                            if (current.getHTFTResult().equals(triggerHTFT)) {

                                if (i + gap < teamList.size()) {
                                    Match resultMatch = teamList.get(i + gap);
                                    pattern.totalChecked++;

                                    if (resultMatch.getHTFTResult().equals(targetHTFT)) {
                                        pattern.foundCount++;
                                        pattern.examples.add(current + " → " + resultMatch);
                                    }
                                }
                            }
                        }
                    }

                    pattern.calculate();

                    if (pattern.accuracy >= minAccuracy && pattern.totalChecked >= 3) {
                        discovered.add(pattern);
                    }
                }
            }
        }

        System.out.println("✅ " + discovered.size() + " HTFT pattern bulundu!\n");
        return discovered;
    }

    // 🔥 DİNAMİK PATTERN KEŞFİ - GOL FARKI BAZLI
    public List<DiscoveredPattern> discoverGoalDiffPatterns(List<Match> matches, int minAccuracy) {
        System.out.println("🔍 GOL FARKI BAZLI PATTERN'LER ARANIYOR...\n");

        List<DiscoveredPattern> discovered = new ArrayList<>();
        Map<String, List<Match>> teamMatches = groupByTeam(matches);

        // 1-5 gol farkı test et
        for (int triggerDiff = 1; triggerDiff <= 5; triggerDiff++) {
            for (int gap = 1; gap <= 5; gap++) {
                for (String targetHTFT : Arrays.asList("2/1", "1/2", "1/0", "2/0")) {

                    DiscoveredPattern pattern = new DiscoveredPattern(
                            "GOL FARKI TRIGGER", triggerDiff + " gol", gap, targetHTFT);

                    for (var entry : teamMatches.entrySet()) {
                        List<Match> teamList = entry.getValue();

                        for (int i = 0; i < teamList.size(); i++) {
                            Match current = teamList.get(i);

                            int diff = Math.abs(current.getFTHome() - current.getFTAway());

                            if (diff == triggerDiff) {

                                if (i + gap < teamList.size()) {
                                    Match resultMatch = teamList.get(i + gap);
                                    pattern.totalChecked++;

                                    if (resultMatch.getHTFTResult().equals(targetHTFT)) {
                                        pattern.foundCount++;
                                        pattern.examples.add(current + " → " + resultMatch);
                                    }
                                }
                            }
                        }
                    }

                    pattern.calculate();

                    if (pattern.accuracy >= minAccuracy && pattern.totalChecked >= 3) {
                        discovered.add(pattern);
                    }
                }
            }
        }

        System.out.println("✅ " + discovered.size() + " gol farkı pattern bulundu!\n");
        return discovered;
    }

    // 🔥 DİNAMİK PATTERN KEŞFİ - ARDIŞIK KOMBINASYON
    public List<DiscoveredPattern> discoverSequentialPatterns(List<Match> matches, int minAccuracy) {
        System.out.println("🔍 ARDIŞIK KOMBİNASYON PATTERN'LER ARANIYOR...\n");

        List<DiscoveredPattern> discovered = new ArrayList<>();
        Map<String, List<Match>> teamMatches = groupByTeam(matches);

        String[] allHTFT = {"1/1", "1/0", "1/2", "0/1", "0/0", "0/2", "2/1", "2/0", "2/2"};

        // İki ardışık HTFT kombinasyonu
        for (String htft1 : allHTFT) {
            for (String htft2 : allHTFT) {
                for (String targetHTFT : Arrays.asList("2/1", "1/2", "1/0", "2/0")) {

                    DiscoveredPattern pattern = new DiscoveredPattern(
                            "ARDIŞIK KOMBO", htft1 + " → " + htft2, 1, targetHTFT);

                    for (var entry : teamMatches.entrySet()) {
                        List<Match> teamList = entry.getValue();

                        for (int i = 0; i < teamList.size() - 2; i++) {
                            if (teamList.get(i).getHTFTResult().equals(htft1) &&
                                    teamList.get(i + 1).getHTFTResult().equals(htft2)) {

                                Match resultMatch = teamList.get(i + 2);
                                pattern.totalChecked++;

                                if (resultMatch.getHTFTResult().equals(targetHTFT)) {
                                    pattern.foundCount++;
                                    pattern.examples.add(teamList.get(i) + " → " + resultMatch);
                                }
                            }
                        }
                    }

                    pattern.calculate();

                    if (pattern.accuracy >= minAccuracy && pattern.totalChecked >= 2) {
                        discovered.add(pattern);
                    }
                }
            }
        }

        System.out.println("✅ " + discovered.size() + " ardışık pattern bulundu!\n");
        return discovered;
    }

    public void close() {
        if (driver != null) driver.quit();
    }

    public static void main(String[] args) {
        MackolikPatternDiscovery analyzer = new MackolikPatternDiscovery();

        try {
            String leagueUrl = "https://arsiv.mackolik.com/Puan-Durumu/1/TURKIYE-Super-Lig";

            // Analiz edilecek sezonlar - SON 5 YIL!
            List<String> seasons = Arrays.asList(
                    "70381",  // 2025/2026
                    "67287",  // 2024/2025
                    "63860",  // 2023/2024
                    "61643",  // 2022/2023
                    "59416"   // 2021/2022
            );

            int maxWeeksPerSeason = 38; // Her sezon için max hafta
            int minAccuracy = 45; // %45 üzeri pattern'leri göster

            System.out.println("🔥🔥🔥 5 SEZONLU SÜPER PATTERN KEŞİF MAKİNESİ 🔥🔥🔥\n");
            System.out.println("⚙️ Analiz Sezonları: 2021-2025 (SON 5 YIL!)");
            System.out.println("⚙️ Hedef HTFT: 1/2, 2/1, 1/X, 2/X");
            System.out.println("⚙️ + Correct Score Patternleri");
            System.out.println("⚙️ Minimum Başarı Oranı: %" + minAccuracy + "\n");

            List<Match> matches = analyzer.scrapeMultipleSeasons(leagueUrl, seasons, maxWeeksPerSeason);

            if (matches.isEmpty()) {
                System.out.println("❌ Veri yok!");
                return;
            }

            System.out.println("🎯 PATTERN ANALİZİ BAŞLIYOR...\n");

            List<DiscoveredPattern> allPatterns = new ArrayList<>();

            // 1. Takım bazlı pattern keşfi
            allPatterns.addAll(analyzer.discoverTeamPatterns(matches, minAccuracy));

            // 2. Skor bazlı pattern keşfi
            allPatterns.addAll(analyzer.discoverScorePatterns(matches, minAccuracy));

            // 3. HTFT bazlı pattern keşfi
            allPatterns.addAll(analyzer.discoverHTFTPatterns(matches, minAccuracy));

            // 4. Gol farkı bazlı pattern keşfi
            allPatterns.addAll(analyzer.discoverGoalDiffPatterns(matches, minAccuracy));

            // 5. Ardışık kombinasyon pattern keşfi
            allPatterns.addAll(analyzer.discoverSequentialPatterns(matches, minAccuracy));

            // 6. 🔥 YENİ! Correct Score pattern keşfi
            allPatterns.addAll(analyzer.discoverCorrectScorePatterns(matches, minAccuracy));

            // Başarı oranına göre sırala
            allPatterns.sort((a, b) -> Double.compare(b.accuracy, a.accuracy));

            System.out.println("\n🏆🏆🏆 KEŞFEDİLEN PATTERN'LER (EN İYİDEN KÖTÜYE) 🏆🏆🏆\n");
            System.out.println("📊 Toplam Maç: " + matches.size());
            System.out.println("💎 Toplam Pattern: " + allPatterns.size() + "\n");

            // En iyi 30 pattern'i göster
            allPatterns.stream().limit(30).forEach(System.out::println);

            if (allPatterns.size() > 0) {
                System.out.println("\n👑 EN GÜÇLÜ PATTERN:");
                System.out.println(allPatterns.get(0));
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            analyzer.close();
        }
    }
}