//package flashscore.weeklydatascraping;
//
//import org.openqa.selenium.*;
//import org.openqa.selenium.chrome.ChromeDriver;
//import org.openqa.selenium.chrome.ChromeOptions;
//import org.openqa.selenium.support.ui.Select;
//import org.openqa.selenium.support.ui.WebDriverWait;
//import org.openqa.selenium.support.ui.ExpectedConditions;
//import java.time.Duration;
//import java.util.*;
//import java.util.stream.Collectors;
//
//public class MackolikPatternDiscovery {
//
//    private WebDriver driver;
//    private WebDriverWait wait;
//
//    static class Match {
//        String date;
//        String homeTeam;
//        String awayTeam;
//        String htScore;
//        String ftScore;
//        int week;
//
//        int getHTHome() { return htScore == null ? -1 : Integer.parseInt(htScore.split("-")[0].trim()); }
//        int getHTAway() { return htScore == null ? -1 : Integer.parseInt(htScore.split("-")[1].trim()); }
//        int getFTHome() { return ftScore == null ? -1 : Integer.parseInt(ftScore.split("-")[0].trim()); }
//        int getFTAway() { return ftScore == null ? -1 : Integer.parseInt(ftScore.split("-")[1].trim()); }
//
//        String getHTFTResult() {
//            if (htScore == null || ftScore == null) return "Unknown";
//            int htH = getHTHome(), htA = getHTAway(), ftH = getFTHome(), ftA = getFTAway();
//            String htR = htH > htA ? "1" : (htH < htA ? "2" : "0");
//            String ftR = ftH > ftA ? "1" : (ftH < ftA ? "2" : "0");
//            return htR + "/" + ftR;
//        }
//
//        boolean isHomeTeam(String team) {
//            return homeTeam.equals(team);
//        }
//
//        boolean isAwayTeam(String team) {
//            return awayTeam.equals(team);
//        }
//
//        String getOpponent(String team) {
//            if (isHomeTeam(team)) return awayTeam;
//            if (isAwayTeam(team)) return homeTeam;
//            return null;
//        }
//
//        @Override
//        public String toString() {
//            return String.format("Hafta %d | %s vs %s | HT:%s FT:%s | 🎯%s",
//                    week, homeTeam, awayTeam, htScore, ftScore, getHTFTResult());
//        }
//    }
//
//    static class DiscoveredPattern {
//        String type;
//        String triggerValue;
//        int gapMatches;
//        String resultHTFT;
//        int foundCount;
//        int totalChecked;
//        double accuracy;
//        List<String> examples;
//
//        DiscoveredPattern(String type, String trigger, int gap, String result) {
//            this.type = type;
//            this.triggerValue = trigger;
//            this.gapMatches = gap;
//            this.resultHTFT = result;
//            this.examples = new ArrayList<>();
//        }
//
//        void calculate() {
//            this.accuracy = totalChecked > 0 ? (double) foundCount / totalChecked * 100 : 0;
//        }
//
//        @Override
//        public String toString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append("\n" + "💎".repeat(50) + "\n");
//            sb.append("🔥 KEŞFEDİLEN PATTERN!\n");
//            sb.append("📌 Tip: ").append(type).append("\n");
//            sb.append("🎯 Trigger: ").append(triggerValue).append("\n");
//            sb.append("⏳ Gap: ").append(gapMatches).append(" maç sonra\n");
//            sb.append("✅ Sonuç: ").append(resultHTFT).append("\n");
//            sb.append("📊 Başarı: ").append(foundCount).append("/").append(totalChecked)
//                    .append(" = ").append(String.format("%.1f%%", accuracy)).append("\n");
//
//            if (!examples.isEmpty()) {
//                sb.append("\n🎲 ÖRNEKLER:\n");
//                examples.stream().limit(3).forEach(ex -> sb.append("   ").append(ex).append("\n"));
//            }
//
//            sb.append("💎".repeat(50) + "\n");
//            return sb.toString();
//        }
//    }
//
//    public MackolikPatternDiscovery() {
//        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--start-maximized");
//        options.addArguments("--disable-blink-features=AutomationControlled");
//        driver = new ChromeDriver(options);
//        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
//    }
//
//    public List<Match> scrapeMultipleSeasons(String leagueUrl, List<String> seasonIds, int maxWeeksPerSeason) {
//        List<Match> allMatches = new ArrayList<>();
//
//        for (String seasonId : seasonIds) {
//            System.out.println("\n" + "🔥".repeat(40));
//            System.out.println("🎯 SEZON: " + seasonId + " YÜKLENİYOR...");
//            System.out.println("🔥".repeat(40) + "\n");
//
//            try {
//                driver.get(leagueUrl);
//                Thread.sleep(3000);
//
//                Select seasonSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cboSeason"))));
//                seasonSelect.selectByValue(seasonId);
//                Thread.sleep(3000);
//
//                WebElement fiksturLink = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Fikstür")));
//                fiksturLink.click();
//                Thread.sleep(3000);
//
//                WebElement firstButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("span.first")));
//                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", firstButton);
//                Thread.sleep(2000);
//                System.out.println("✅ İlk haftaya gidildi!\n");
//
//                int weekCounter = 0;
//                int seasonMatchCount = 0;
//
//                while (weekCounter < maxWeeksPerSeason) {
//                    weekCounter++;
//
//                    Select weekSelect = new Select(driver.findElement(By.id("cboWeek")));
//                    String currentWeek = weekSelect.getFirstSelectedOption().getText();
//                    System.out.println("📅 " + currentWeek + " işleniyor...");
//
//                    WebElement table = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#dvFixtureInner table.list-table")));
//                    List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));
//
//                    int matchesInWeek = 0;
//
//                    for (WebElement row : rows) {
//                        try {
//                            if (row.getAttribute("class") != null && row.getAttribute("class").contains("table-header")) {
//                                continue;
//                            }
//
//                            List<WebElement> cells = row.findElements(By.tagName("td"));
//                            if (cells.size() < 9) continue;
//
//                            Match match = new Match();
//                            match.week = weekCounter;
//                            match.date = cells.get(0).getText().trim();
//                            match.homeTeam = cells.get(3).getText().trim();
//                            match.awayTeam = cells.get(7).getText().trim();
//
//                            String ftScore = cells.get(5).getText().trim();
//                            String htScore = cells.get(8).getText().trim();
//
//                            if (!ftScore.isEmpty() && !htScore.isEmpty() &&
//                                ftScore.contains("-") && htScore.contains("-") &&
//                                !ftScore.equals("-") && !htScore.equals("-")) {
//                                match.ftScore = ftScore;
//                                match.htScore = htScore;
//                                allMatches.add(match);
//                                matchesInWeek++;
//                                seasonMatchCount++;
//                            }
//
//                        } catch (Exception e) {
//                            // Skip row parse error
//                        }
//                    }
//
//                    System.out.println("   ✅ " + matchesInWeek + " maç (Sezon: " + seasonMatchCount + " | Toplam: " + allMatches.size() + ")");
//
//                    if (matchesInWeek == 0) {
//                        System.out.println("   ⚠️ Bu hafta henüz oynanmamış, bir sonraki sezona geçiliyor!");
//                        break;
//                    }
//
//                    try {
//                        WebElement nextButton = driver.findElement(By.cssSelector("span.next"));
//                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButton);
//                        Thread.sleep(2000);
//                    } catch (Exception e) {
//                        System.out.println("   ✅ Sezon sonu! Toplam " + seasonMatchCount + " maç eklendi.");
//                        break;
//                    }
//                }
//
//                System.out.println("\n✅ SEZON " + seasonId + " TAMAMLANDI: " + seasonMatchCount + " maç\n");
//
//            } catch (Exception e) {
//                System.out.println("❌ Sezon " + seasonId + " hatası: " + e.getMessage());
//                e.printStackTrace();
//            }
//        }
//
//        System.out.println("\n🏆🏆🏆 TÜM SEZONLAR TAMAMLANDI! 🏆🏆🏆");
//        System.out.println("📊 TOPLAM " + allMatches.size() + " MAÇ YÜKLENDİ!\n");
//
//        return allMatches;
//    }
//
//    private Map<String, List<Match>> groupByTeam(List<Match> matches) {
//        Map<String, List<Match>> map = new HashMap<>();
//
//        for (Match m : matches) {
//            map.putIfAbsent(m.homeTeam, new ArrayList<>());
//            map.putIfAbsent(m.awayTeam, new ArrayList<>());
//            map.get(m.homeTeam).add(m);
//            map.get(m.awayTeam).add(m);
//        }
//
//        for (var entry : map.entrySet()) {
//            entry.getValue().sort(Comparator.comparingInt(m -> m.week));
//        }
//
//        return map;
//    }
//
//    // 🔥 TAKIM + RAKİP KOMBİNASYONU
//    public List<DiscoveredPattern> discoverTeamOpponentPatterns(List<Match> matches, int minAccuracy) {
//        System.out.println("🔍 TAKIM + RAKİP KOMBİNASYON PATTERN'LERİ ARANIYOR...\n");
//
//        List<DiscoveredPattern> discovered = new ArrayList<>();
//        Map<String, List<Match>> teamMatches = groupByTeam(matches);
//
//        // Tüm takım-rakip kombinasyonlarını test et
//        for (String team : teamMatches.keySet()) {
//            List<Match> teamGames = teamMatches.get(team);
//
//            // Bu takımın oynadığı tüm rakipleri bul
//            Set<String> opponents = new HashSet<>();
//            for (Match m : teamGames) {
//                String opponent = m.getOpponent(team);
//                if (opponent != null) {
//                    opponents.add(opponent);
//                }
//            }
//
//            // Her rakiple olan pattern'leri ara
//            for (String opponent : opponents) {
//                for (int gap = 1; gap <= 5; gap++) {
//                    for (String targetHTFT : Arrays.asList("2/1", "1/2", "1/0", "2/0")) {
//
//                        DiscoveredPattern pattern = new DiscoveredPattern(
//                                "TAKIM+RAKİP",
//                                team + " vs " + opponent,
//                                gap,
//                                targetHTFT
//                        );
//
//                        // Bu takımın bu rakiple oynadığı maçları bul
//                        for (int i = 0; i < teamGames.size(); i++) {
//                            Match current = teamGames.get(i);
//                            if (current.getOpponent(team) != null && current.getOpponent(team).equals(opponent)) {
//
//                                // Gap sonraki maçı kontrol et
//                                if (i + gap < teamGames.size()) {
//                                    Match resultMatch = teamGames.get(i + gap);
//                                    pattern.totalChecked++;
//
//                                    if (resultMatch.getHTFTResult().equals(targetHTFT)) {
//                                        pattern.foundCount++;
//                                        pattern.examples.add(current + " → " + gap + " maç sonra → " + resultMatch);
//                                    }
//                                }
//                            }
//                        }
//
//                        pattern.calculate();
//                        if (pattern.totalChecked >= 3 && pattern.accuracy >= minAccuracy) {
//                            discovered.add(pattern);
//                        }
//                    }
//                }
//            }
//        }
//
//        System.out.println("✅ " + discovered.size() + " takım+rakip pattern bulundu!\n");
//        return discovered;
//    }
//
//    // 🔥 SKOR + TAKIM KOMBİNASYONU
//    public List<DiscoveredPattern> discoverScoreTeamPatterns(List<Match> matches, int minAccuracy) {
//        System.out.println("🔍 SKOR + TAKIM KOMBİNASYON PATTERN'LERİ ARANIYOR...\n");
//
//        List<DiscoveredPattern> discovered = new ArrayList<>();
//        Map<String, List<Match>> teamMatches = groupByTeam(matches);
//
//        // En popüler skorları bul
//        Map<String, Integer> scoreFrequency = new HashMap<>();
//        matches.forEach(m -> {
//            if (m.ftScore != null) {
//                scoreFrequency.put(m.ftScore, scoreFrequency.getOrDefault(m.ftScore, 0) + 1);
//            }
//        });
//
//        List<String> popularScores = scoreFrequency.entrySet().stream()
//                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
//                .limit(10)
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toList());
//
//        // Her takım ve popüler skor kombinasyonunu test et
//        for (String team : teamMatches.keySet()) {
//            for (String score : popularScores) {
//                for (int gap = 1; gap <= 5; gap++) {
//                    for (String targetHTFT : Arrays.asList("2/1", "1/2", "1/0", "2/0")) {
//
//                        DiscoveredPattern pattern = new DiscoveredPattern(
//                                "SKOR+TAKIM",
//                                team + " skor: " + score,
//                                gap,
//                                targetHTFT
//                        );
//
//                        List<Match> teamGames = teamMatches.get(team);
//
//                        for (int i = 0; i < teamGames.size(); i++) {
//                            Match current = teamGames.get(i);
//
//                            // Bu takım bu skoru aldı mı?
//                            if (score.equals(current.ftScore)) {
//
//                                // Gap sonraki maçı kontrol et
//                                if (i + gap < teamGames.size()) {
//                                    Match resultMatch = teamGames.get(i + gap);
//                                    pattern.totalChecked++;
//
//                                    if (resultMatch.getHTFTResult().equals(targetHTFT)) {
//                                        pattern.foundCount++;
//                                        pattern.examples.add(current + " → " + gap + " maç sonra → " + resultMatch);
//                                    }
//                                }
//                            }
//                        }
//
//                        pattern.calculate();
//                        if (pattern.totalChecked >= 3 && pattern.accuracy >= minAccuracy) {
//                            discovered.add(pattern);
//                        }
//                    }
//                }
//            }
//        }
//
//        System.out.println("✅ " + discovered.size() + " skor+takım pattern bulundu!\n");
//        return discovered;
//    }
//
//    // 🔥 TAKIM + SKOR + RAKİP KOMBİNASYONU (EN DETAYLI)
//    public List<DiscoveredPattern> discoverTeamScoreOpponentPatterns(List<Match> matches, int minAccuracy) {
//        System.out.println("🔍 TAKIM + SKOR + RAKİP KOMBİNASYON PATTERN'LERİ ARANIYOR...\n");
//
//        List<DiscoveredPattern> discovered = new ArrayList<>();
//        Map<String, List<Match>> teamMatches = groupByTeam(matches);
//
//        // Popüler skorlar
//        Map<String, Integer> scoreFrequency = new HashMap<>();
//        matches.forEach(m -> {
//            if (m.ftScore != null) {
//                scoreFrequency.put(m.ftScore, scoreFrequency.getOrDefault(m.ftScore, 0) + 1);
//            }
//        });
//
//        List<String> popularScores = scoreFrequency.entrySet().stream()
//                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
//                .limit(8)
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toList());
//
//        for (String team : teamMatches.keySet()) {
//            List<Match> teamGames = teamMatches.get(team);
//
//            // Bu takımın rakiplerini bul
//            Set<String> opponents = new HashSet<>();
//            for (Match m : teamGames) {
//                String opponent = m.getOpponent(team);
//                if (opponent != null) {
//                    opponents.add(opponent);
//                }
//            }
//
//            // Her rakip ve skor kombinasyonu
//            for (String opponent : opponents) {
//                for (String score : popularScores) {
//                    for (int gap = 1; gap <= 3; gap++) {
//                        for (String targetHTFT : Arrays.asList("2/1", "1/2")) {
//
//                            DiscoveredPattern pattern = new DiscoveredPattern(
//                                    "TAKIM+SKOR+RAKİP",
//                                    team + " vs " + opponent + " skor: " + score,
//                                    gap,
//                                    targetHTFT
//                            );
//
//                            for (int i = 0; i < teamGames.size(); i++) {
//                                Match current = teamGames.get(i);
//
//                                // Bu takım bu rakibe karşı bu skoru aldı mı?
//                                if (current.getOpponent(team) != null &&
//                                    current.getOpponent(team).equals(opponent) &&
//                                    score.equals(current.ftScore)) {
//
//                                    if (i + gap < teamGames.size()) {
//                                        Match resultMatch = teamGames.get(i + gap);
//                                        pattern.totalChecked++;
//
//                                        if (resultMatch.getHTFTResult().equals(targetHTFT)) {
//                                            pattern.foundCount++;
//                                            pattern.examples.add(current + " → " + gap + " maç sonra → " + resultMatch);
//                                        }
//                                    }
//                                }
//                            }
//
//                            pattern.calculate();
//                            if (pattern.totalChecked >= 2 && pattern.accuracy >= minAccuracy) {
//                                discovered.add(pattern);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        System.out.println("✅ " + discovered.size() + " takım+skor+rakip pattern bulundu!\n");
//        return discovered;
//    }
//
//    // 🔥 SKOR SEQUENCE PATTERN'LERİ
//    public List<DiscoveredPattern> discoverScoreSequencePatterns(List<Match> matches, int minAccuracy) {
//        System.out.println("🔍 SKOR SEQUENCE PATTERN'LERİ ARANIYOR...\n");
//
//        List<DiscoveredPattern> discovered = new ArrayList<>();
//        Map<String, List<Match>> teamMatches = groupByTeam(matches);
//
//        // Popüler skorlar
//        Map<String, Integer> scoreFrequency = new HashMap<>();
//        matches.forEach(m -> {
//            if (m.ftScore != null) {
//                scoreFrequency.put(m.ftScore, scoreFrequency.getOrDefault(m.ftScore, 0) + 1);
//            }
//        });
//
//        List<String> popularScores = scoreFrequency.entrySet().stream()
//                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
//                .limit(12)
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toList());
//
//        // İki skorun sequence'i
//        for (String score1 : popularScores) {
//            for (String score2 : popularScores) {
//                for (String targetHTFT : Arrays.asList("2/1", "1/2", "1/0", "2/0")) {
//
//                    DiscoveredPattern pattern = new DiscoveredPattern(
//                            "SKOR SEQUENCE",
//                            score1 + " → " + score2,
//                            1,
//                            targetHTFT
//                    );
//
//                    for (var entry : teamMatches.entrySet()) {
//                        List<Match> teamGames = entry.getValue();
//
//                        for (int i = 0; i < teamGames.size() - 2; i++) {
//                            Match first = teamGames.get(i);
//                            Match second = teamGames.get(i + 1);
//
//                            if (score1.equals(first.ftScore) && score2.equals(second.ftScore)) {
//                                Match resultMatch = teamGames.get(i + 2);
//                                pattern.totalChecked++;
//
//                                if (resultMatch.getHTFTResult().equals(targetHTFT)) {
//                                    pattern.foundCount++;
//                                    pattern.examples.add(first + " → " + second + " → " + resultMatch);
//                                }
//                            }
//                        }
//                    }
//
//                    pattern.calculate();
//                    if (pattern.totalChecked >= 3 && pattern.accuracy >= minAccuracy) {
//                        discovered.add(pattern);
//                    }
//                }
//            }
//        }
//
//        System.out.println("✅ " + discovered.size() + " skor sequence pattern bulundu!\n");
//        return discovered;
//    }
//
//    // 🔥 EV/DIŞ SAHA SKOR PATTERN'LERİ
//    public List<DiscoveredPattern> discoverHomeAwayScorePatterns(List<Match> matches, int minAccuracy) {
//        System.out.println("🔍 EV/DIŞ SAHA SKOR PATTERN'LERİ ARANIYOR...\n");
//
//        List<DiscoveredPattern> discovered = new ArrayList<>();
//        Map<String, List<Match>> teamMatches = groupByTeam(matches);
//
//        // Popüler skorlar
//        Map<String, Integer> scoreFrequency = new HashMap<>();
//        matches.forEach(m -> {
//            if (m.ftScore != null) {
//                scoreFrequency.put(m.ftScore, scoreFrequency.getOrDefault(m.ftScore, 0) + 1);
//            }
//        });
//
//        List<String> popularScores = scoreFrequency.entrySet().stream()
//                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
//                .limit(10)
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toList());
//
//        for (String team : teamMatches.keySet()) {
//            for (String score : popularScores) {
//                for (String venue : Arrays.asList("EV", "DIŞ")) {
//                    for (int gap = 1; gap <= 4; gap++) {
//                        for (String targetHTFT : Arrays.asList("2/1", "1/2")) {
//
//                            DiscoveredPattern pattern = new DiscoveredPattern(
//                                    "SAHA+SKOR",
//                                    team + " " + venue + " saha skor: " + score,
//                                    gap,
//                                    targetHTFT
//                            );
//
//                            List<Match> teamGames = teamMatches.get(team);
//
//                            for (int i = 0; i < teamGames.size(); i++) {
//                                Match current = teamGames.get(i);
//
//                                boolean isHomeMatch = current.isHomeTeam(team);
//                                boolean venueMatch = (venue.equals("EV") && isHomeMatch) ||
//                                                     (venue.equals("DIŞ") && !isHomeMatch);
//
//                                if (venueMatch && score.equals(current.ftScore)) {
//                                    if (i + gap < teamGames.size()) {
//                                        Match resultMatch = teamGames.get(i + gap);
//                                        pattern.totalChecked++;
//
//                                        if (resultMatch.getHTFTResult().equals(targetHTFT)) {
//                                            pattern.foundCount++;
//                                            pattern.examples.add(current + " → " + gap + " maç sonra → " + resultMatch);
//                                        }
//                                    }
//                                }
//                            }
//
//                            pattern.calculate();
//                            if (pattern.totalChecked >= 2 && pattern.accuracy >= minAccuracy) {
//                                discovered.add(pattern);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        System.out.println("✅ " + discovered.size() + " ev/dış saha skor pattern bulundu!\n");
//        return discovered;
//    }
//
//    // İstatistik gösterimi
//    private static void showPatternStatistics(List<DiscoveredPattern> patterns) {
//        System.out.println("\n📈 PATTERN İSTATİSTİKLERİ:");
//
//        if (patterns.isEmpty()) {
//            System.out.println("❌ İstatistik hesaplanacak pattern bulunamadı!");
//            return;
//        }
//
//        double maxAccuracy = patterns.stream().mapToDouble(p -> p.accuracy).max().orElse(0);
//        double avgAccuracy = patterns.stream().mapToDouble(p -> p.accuracy).average().orElse(0);
//
//        System.out.println("✅ En Yüksek Doğruluk: %" + String.format("%.1f", maxAccuracy));
//        System.out.println("📊 Ortalama Doğruluk: %" + String.format("%.1f", avgAccuracy));
//        System.out.println("🎯 Toplam Benzersiz Pattern: " + patterns.size());
//
//        // Pattern tiplerine göre dağılım
//        Map<String, Long> typeDistribution = patterns.stream()
//                .collect(Collectors.groupingBy(p -> p.type, Collectors.counting()));
//
//        System.out.println("🏷️  Tip Dağılımı: " + typeDistribution);
//
//        // Başarı dağılımı
//        long highAccuracy = patterns.stream().filter(p -> p.accuracy >= 70).count();
//        long mediumAccuracy = patterns.stream().filter(p -> p.accuracy >= 50 && p.accuracy < 70).count();
//        long lowAccuracy = patterns.stream().filter(p -> p.accuracy < 50).count();
//
//        System.out.println("🎖️  Başarı Dağılımı:");
//        System.out.println("   🥇 %70+ Başarı: " + highAccuracy + " pattern");
//        System.out.println("   🥈 %50-70 Başarı: " + mediumAccuracy + " pattern");
//        System.out.println("   🥉 %50- Başarı: " + lowAccuracy + " pattern");
//    }
//
//    public void close() {
//        if (driver != null) driver.quit();
//    }
//
//    public static void main(String[] args) {
//        MackolikPatternDiscovery analyzer = new MackolikPatternDiscovery();
//
//        try {
//            String leagueUrl = "https://arsiv.mackolik.com/Puan-Durumu/1/TURKIYE-Super-Lig";
//
//            List<String> seasons = Arrays.asList(
//                    "70381",  // 2025/2026
//                    "67287",  // 2024/2025
//                    "63860",  // 2023/2024
//                    "61643",  // 2022/2023
//                    "59416"   // 2021/2022
//            );
//
//            int maxWeeksPerSeason = 38;
//            int minAccuracy = 40; // %40 minimum başarı
//
//            System.out.println("🔥🔥🔥 ANLAMLI PATTERN KEŞİF MAKİNESİ 🔥🔥🔥\n");
//            System.out.println("⚙️ Analiz Sezonları: 2021-2025 (SON 5 YIL!)");
//            System.out.println("⚙️ Pattern Tipleri:");
//            System.out.println("   • Takım + Rakip Kombinasyonları");
//            System.out.println("   • Skor + Takım Kombinasyonları");
//            System.out.println("   • Takım + Skor + Rakip Kombinasyonları");
//            System.out.println("   • Skor Sequence'leri");
//            System.out.println("   • Ev/Dış Saha Skor Pattern'leri");
//            System.out.println("⚙️ Minimum Başarı: %" + minAccuracy + "\n");
//
//            List<Match> matches = analyzer.scrapeMultipleSeasons(leagueUrl, seasons, maxWeeksPerSeason);
//
//            if (matches.isEmpty()) {
//                System.out.println("❌ Veri yok!");
//                return;
//            }
//
//            System.out.println("🎯 ANLAMLI PATTERN ANALİZİ BAŞLIYOR...\n");
//
//            List<DiscoveredPattern> allPatterns = new ArrayList<>();
//
//            // ANLAMLI PATTERN KEŞİFLERİ
//            allPatterns.addAll(analyzer.discoverTeamOpponentPatterns(matches, minAccuracy));
//            allPatterns.addAll(analyzer.discoverScoreTeamPatterns(matches, minAccuracy));
//            allPatterns.addAll(analyzer.discoverTeamScoreOpponentPatterns(matches, minAccuracy));
//            allPatterns.addAll(analyzer.discoverScoreSequencePatterns(matches, minAccuracy));
//            allPatterns.addAll(analyzer.discoverHomeAwayScorePatterns(matches, minAccuracy));
//
//            // Başarı oranına göre sırala
//            allPatterns.sort((a, b) -> Double.compare(b.accuracy, a.accuracy));
//
//            System.out.println("\n🏆🏆🏆 KEŞFEDİLEN ANLAMLI PATTERN'LER 🏆🏆🏆\n");
//            System.out.println("📊 Toplam Maç: " + matches.size());
//            System.out.println("💎 Toplam Pattern: " + allPatterns.size());
//            System.out.println("🎯 Minimum Başarı: %" + minAccuracy + "\n");
//
//            // Tüm pattern'leri göster
//            allPatterns.forEach(System.out::println);
//
//            if (!allPatterns.isEmpty()) {
//                System.out.println("\n👑 EN GÜÇLÜ " + Math.min(5, allPatterns.size()) + " PATTERN:");
//                allPatterns.stream().limit(5).forEach(System.out::println);
//
//                // İstatistikleri göster
//                showPatternStatistics(allPatterns);
//            } else {
//                System.out.println("❌ Hiç anlamlı pattern bulunamadı!");
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            analyzer.close();
//        }
//    }
//}