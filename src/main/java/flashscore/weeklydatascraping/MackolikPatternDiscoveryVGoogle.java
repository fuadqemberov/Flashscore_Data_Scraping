package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class MackolikPatternDiscoveryVGoogle {

    private WebDriver driver;
    private WebDriverWait wait;

    // ==========================================
    // 1. MATCH SINIFI (Tarih Motoru Güncellendi)
    // ==========================================
    static class Match {
        String date;      // "26/10" gibi
        String homeTeam;
        String awayTeam;
        String htScore;
        String ftScore;
        int week;
        String season;    // "2025/2026" gibi

        // Dinamik Tarih Algılayıcı: Sezon stringine göre yılı hesaplar
        LocalDate getLocalDate() {
            try {
                if (date == null || !date.contains("/")) return LocalDate.MIN;

                String[] dateParts = date.split("/");
                int day = Integer.parseInt(dateParts[0].trim());
                int month = Integer.parseInt(dateParts[1].trim());

                int startYear = 2024; // Varsayılan
                int endYear = 2025;

                if (season != null && season.contains("/")) {
                    String[] seasonParts = season.split("/");
                    try {
                        startYear = Integer.parseInt(seasonParts[0].trim());
                        endYear = Integer.parseInt(seasonParts[1].trim());
                    } catch (NumberFormatException e) {
                        // Parse hatası olursa varsayılanı koru
                    }
                }

                int year;
                // Futbol sezonu mantığı: Temmuz(7) ve sonrası başlangıç yılıdır (Örn: 2025),
                // Ocak-Haziran bitiş yılıdır (Örn: 2026).
                if (month >= 7) {
                    year = startYear;
                } else {
                    year = endYear;
                }

                return LocalDate.of(year, month, day);
            } catch (Exception e) {
                return LocalDate.MIN;
            }
        }

        int getHTHome() { try { return Integer.parseInt(htScore.split("-")[0].trim()); } catch (Exception e) { return -1; } }
        int getHTAway() { try { return Integer.parseInt(htScore.split("-")[1].trim()); } catch (Exception e) { return -1; } }
        int getFTHome() { try { return Integer.parseInt(ftScore.split("-")[0].trim()); } catch (Exception e) { return -1; } }
        int getFTAway() { try { return Integer.parseInt(ftScore.split("-")[1].trim()); } catch (Exception e) { return -1; } }

        String getHTFTResult() {
            int htH = getHTHome(), htA = getHTAway(), ftH = getFTHome(), ftA = getFTAway();
            if (htH == -1) return "Unknown";
            String htR = htH > htA ? "1" : (htH < htA ? "2" : "X");
            String ftR = ftH > ftA ? "1" : (ftH < ftA ? "2" : "X");
            return htR + "/" + ftR;
        }

        String getHTFTCategory() {
            String htft = getHTFTResult();
            if (htft.equals("1/2") || htft.equals("2/1")) return "REVERSE";
            if (htft.equals("1/X") || htft.equals("2/X")) return "DRAW_INVOLVED";
            return "IGNORE";
        }

        boolean isHomeTeam(String team) { return homeTeam.equals(team); }
        boolean isAwayTeam(String team) { return awayTeam.equals(team); }

        @Override
        public String toString() {
            return String.format("%s | W%d | %s vs %s | %s", season, week, homeTeam, awayTeam, ftScore);
        }

        // Skorları normalize et (örn: null check)
        static String getNormalizedScore(String score) {
            if (score == null || !score.contains("-")) return null;
            return score.trim();
        }
    }

    // ==========================================
    // 2. PATTERN SINIFI
    // ==========================================
    static class DiscoveredPattern {
        String type;
        String triggerValue; // Dörtlü için bagKey
        int foundCount;
        int totalChecked;
        double accuracy;
        List<String> examples = new ArrayList<>();
        List<String> seasons = new ArrayList<>();

        // Dörtlü Cycle Özel Alanları
        String bagKey = "";
        Set<String> teamsInPattern = new HashSet<>();
        List<List<String>> observedSequences = new ArrayList<>();

        // Diğer patternler için
        Map<Integer, Integer> reverseAfterGap = new HashMap<>();
        Map<Integer, Integer> drawInvolvedAfterGap = new HashMap<>();
        Map<Integer, Integer> totalAfterGap = new HashMap<>();
        String posteriorMatch = "";

        DiscoveredPattern(String type, String trigger) {
            this.type = type;
            this.triggerValue = trigger;
        }

        void calculate() {
            this.accuracy = totalChecked > 0 ? (double) foundCount / totalChecked * 100 : 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n").append("💎".repeat(50)).append("\n");

            if (type.equals("DORTLU_CYCLE")) {
                sb.append("🔥 KEŞFEDİLEN DÖRTLÜ ÇEVRİM PATTERN!\n");
                sb.append("📌 Tip: ").append(type).append("\n");
                sb.append("🎯 Pattern (Multiset): ").append(bagKey).append("\n");
                sb.append("📊 Tekrar Sayısı: ").append(foundCount).append(" kez (non-overlapping)\n");
                sb.append("✅ Başarı: ").append(String.format("%.1f%%", accuracy)).append("\n");
                sb.append("👥 Takımlar: ").append(String.join(", ", teamsInPattern)).append("\n");
                sb.append("🏆 Sezonlar: ").append(String.join(", ", seasons)).append("\n");

                if (!observedSequences.isEmpty()) {
                    sb.append("\n🔄 GÖZLENEN ROTASYONLAR:\n");
                    Set<String> unique = new HashSet<>();
                    for (List<String> seq : observedSequences) {
                        String s = String.join(" → ", seq);
                        if (unique.add(s)) sb.append("   ").append(s).append("\n");
                    }
                }

                sb.append("\n🎲 ÖRNEKLER (Toplam ").append(examples.size()).append("):\n");
                for (String ex : examples) sb.append("   ").append(ex).append("\n");
            } else {
                // Diğer tipler için standart çıktı
                sb.append("🔥 PATTERN: ").append(type).append(" | Trigger: ").append(triggerValue).append("\n");
                sb.append("📊 Count: ").append(foundCount).append(" | Accuracy: ").append(String.format("%.1f%%", accuracy)).append("\n");
                if(!posteriorMatch.isEmpty()) sb.append("🔜 Sonrası: ").append(posteriorMatch).append("\n");
                if(!examples.isEmpty()) {
                    sb.append("🎲 Örnekler:\n");
                    examples.stream().limit(5).forEach(e -> sb.append("   ").append(e).append("\n"));
                }
            }

            sb.append("💎".repeat(50)).append("\n");
            return sb.toString();
        }
    }

    // ==========================================
    // 3. YARDIMCI SINIFLAR
    // ==========================================
    static class WindowInfo {
        int startIndex;
        String bagKey;
        List<String> window;
        WindowInfo(int s, String b, List<String> w) { startIndex = s; bagKey = b; window = w; }
    }

    // ==========================================
    // 4. ANA SINIF METOTLARI
    // ==========================================

    public MackolikPatternDiscoveryVGoogle() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void showAvailableLeagues() {
        System.out.println("🏆 Lütfen Lig URL'sini yapıştırın (Örn: Türkiye Süper Lig).");
    }

    public Map<String, String> getAvailableSeasonsMap(String leagueUrl) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            driver.get(leagueUrl);
            Thread.sleep(3000);
            Select seasonSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cboSeason"))));
            for (WebElement opt : seasonSelect.getOptions()) {
                if (!opt.getText().isEmpty()) {
                    map.put(opt.getText().trim(), opt.getAttribute("value"));
                    System.out.println("📅 " + opt.getText() + " (ID: " + opt.getAttribute("value") + ")");
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Sezonlar alınamadı: " + e.getMessage());
        }
        return map;
    }

    // SCALABLE SCRAPER
    public List<Match> scrapeMultipleSeasons(List<String> seasonIds, int maxWeeks) {
        List<Match> allMatches = new ArrayList<>();

        for (String seasonId : seasonIds) {
            try {
                String url = "https://arsiv.mackolik.com/Standings/Default.aspx?sId=" + seasonId;
                driver.get(url);
                Thread.sleep(3000);

                Select seasonSelect = new Select(driver.findElement(By.id("cboSeason")));
                String currentSeasonName = seasonSelect.getFirstSelectedOption().getText().trim();
                System.out.println("\n🎯 Sezon Yükleniyor: " + currentSeasonName);

                // Fikstür Tıklama
                try {
                    WebElement fixBtn = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Fikstür")));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", fixBtn);
                    Thread.sleep(3000);
                } catch (Exception e) {
                    driver.get(url.replace("Standings", "Fixture"));
                    Thread.sleep(3000);
                }

                // İlk Haftaya Git
                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", driver.findElement(By.cssSelector("span.first")));
                    Thread.sleep(2000);
                } catch (Exception e) {}

                int week = 0;
                int emptyStreak = 0;

                while (week < maxWeeks && emptyStreak < 2) {
                    week++;
                    Select weekSel = new Select(driver.findElement(By.id("cboWeek")));
                    System.out.println("   📅 " + weekSel.getFirstSelectedOption().getText() + " taranıyor...");

                    List<WebElement> rows = driver.findElements(By.cssSelector("#dvFixtureInner table.list-table tbody tr"));
                    int count = 0;

                    for (WebElement row : rows) {
                        try {
                            if (row.getAttribute("class").contains("table-header")) continue;
                            List<WebElement> cells = row.findElements(By.tagName("td"));
                            if (cells.size() < 9) continue;

                            Match m = new Match();
                            m.season = currentSeasonName;
                            m.week = week;
                            m.date = cells.get(0).getText().trim();
                            m.homeTeam = cells.get(3).getText().trim();
                            m.awayTeam = cells.get(7).getText().trim();
                            m.ftScore = cells.get(5).getText().trim();
                            m.htScore = cells.get(8).getText().trim();

                            if (m.ftScore.contains("-") && m.htScore.contains("-")) {
                                allMatches.add(m);
                                count++;
                            }
                        } catch (Exception e) {}
                    }

                    if (count == 0) emptyStreak++; else emptyStreak = 0;

                    // Sonraki Hafta
                    try {
                        WebElement next = driver.findElement(By.cssSelector("span.next"));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", next);
                        Thread.sleep(2500);
                    } catch (Exception e) { break; }
                }

            } catch (Exception e) {
                System.out.println("❌ Sezon hatası: " + e.getMessage());
            }
        }
        return allMatches;
    }

    // ==========================================
    // 5. KEŞİF ALGORİTMALARI
    // ==========================================

    // Simetrik Skor (0-1 ile 1-0 aynı kabul edilir)
    private static String getSymmetricalScore(Match m, String team) {
        if (m.ftScore == null || !m.ftScore.contains("-")) return null;
        try {
            String[] parts = m.ftScore.split("-");
            int h = Integer.parseInt(parts[0].trim());
            int a = Integer.parseInt(parts[1].trim());
            int teamScore = m.isHomeTeam(team) ? h : a;
            int oppScore = m.isHomeTeam(team) ? a : h;
            return Math.min(teamScore, oppScore) + "-" + Math.max(teamScore, oppScore);
        } catch (Exception e) { return null; }
    }

    // Takımları grupla ve TARİHE GÖRE DOĞRU SIRALA
    private Map<String, List<Match>> groupByTeamSorted(List<Match> matches) {
        Map<String, List<Match>> map = new HashMap<>();
        for (Match m : matches) {
            map.computeIfAbsent(m.homeTeam, k -> new ArrayList<>()).add(m);
            map.computeIfAbsent(m.awayTeam, k -> new ArrayList<>()).add(m);
        }
        // getLocalDate kullanarak doğru kronolojik sıralama yapıyoruz
        for (List<Match> list : map.values()) {
            list.sort(Comparator.comparing(Match::getLocalDate));
        }
        return map;
    }

    public List<DiscoveredPattern> discoverQuartetCyclePatterns(List<Match> matches) {
        System.out.println("\n🔍 DÖRTLÜ ÇEVRİM (0-1=1-0) PATTERN'LERİ ARANIYOR...");

        Map<String, List<Match>> teamMatches = groupByTeamSorted(matches);
        Map<String, List<String>> bagKeyToOccurrences = new HashMap<>();
        Map<String, List<String>> bagKeyToExamples = new HashMap<>();
        Map<String, Set<String>> bagKeyToSeasons = new HashMap<>();
        Map<String, Set<String>> bagKeyToTeams = new HashMap<>();
        Map<String, List<String>> occurrenceToWindow = new HashMap<>();

        for (String team : teamMatches.keySet()) {
            List<Match> games = teamMatches.get(team);

            // Sezon bazlı indeksleme (Sezonlar arası pattern aramıyoruz, sezon içi bakıyoruz)
            Map<String, List<Integer>> seasonIndices = new HashMap<>();
            for (int i = 0; i < games.size(); i++) {
                seasonIndices.computeIfAbsent(games.get(i).season, k -> new ArrayList<>()).add(i);
            }

            for (String season : seasonIndices.keySet()) {
                List<Integer> idxs = seasonIndices.get(season);
                List<WindowInfo> windows = new ArrayList<>();

                for (int i : idxs) {
                    if (i + 4 <= games.size()) {
                        // Sınır kontrolü: Penceredeki tüm maçlar aynı sezonda olmalı
                        if (!games.get(i+3).season.equals(season)) continue;

                        List<String> scores = new ArrayList<>();
                        boolean valid = true;
                        for (int k=0; k<4; k++) {
                            String sym = getSymmetricalScore(games.get(i+k), team);
                            if (sym == null) { valid = false; break; }
                            scores.add(sym);
                        }
                        if (!valid) continue;

                        List<String> sorted = new ArrayList<>(scores);
                        Collections.sort(sorted);
                        String bagKey = String.join("|", sorted);
                        windows.add(new WindowInfo(i, bagKey, scores));
                    }
                }

                // Non-overlapping seçimi
                Map<String, List<WindowInfo>> grouped = new HashMap<>();
                for (WindowInfo w : windows) grouped.computeIfAbsent(w.bagKey, k -> new ArrayList<>()).add(w);

                for (String bagKey : grouped.keySet()) {
                    List<WindowInfo> candidates = grouped.get(bagKey);
                    candidates.sort(Comparator.comparingInt(w -> w.startIndex));

                    int lastEnd = -1;
                    for (WindowInfo w : candidates) {
                        if (w.startIndex >= lastEnd) {
                            String occKey = team + "|" + season + "|" + w.startIndex;
                            bagKeyToOccurrences.computeIfAbsent(bagKey, k -> new ArrayList<>()).add(occKey);
                            occurrenceToWindow.put(occKey, w.window);
                            bagKeyToSeasons.computeIfAbsent(bagKey, k -> new HashSet<>()).add(season);
                            bagKeyToTeams.computeIfAbsent(bagKey, k -> new HashSet<>()).add(team);

                            StringBuilder ex = new StringBuilder(team + " | " + season + " | Idx:" + w.startIndex);
                            for(int k=0; k<4; k++) {
                                Match m = games.get(w.startIndex+k);
                                ex.append("\n      ").append(m.date).append(" ").append(m.homeTeam).append("-").append(m.awayTeam)
                                        .append(" FT:").append(m.ftScore).append(" (Sym:").append(w.window.get(k)).append(")");
                            }
                            bagKeyToExamples.computeIfAbsent(bagKey, k -> new ArrayList<>()).add(ex.toString());

                            lastEnd = w.startIndex + 4;
                        }
                    }
                }
            }
        }

        List<DiscoveredPattern> results = new ArrayList<>();
        for (String bagKey : bagKeyToOccurrences.keySet()) {
            List<String> occs = bagKeyToOccurrences.get(bagKey);
            if (occs.size() >= 2) { // En az 2 tekrar
                DiscoveredPattern p = new DiscoveredPattern("DORTLU_CYCLE", bagKey);
                p.bagKey = bagKey;
                p.foundCount = occs.size();
                p.totalChecked = occs.size(); // Sadece pozitifler sayıldı
                p.calculate();
                p.seasons.addAll(bagKeyToSeasons.get(bagKey));
                p.teamsInPattern.addAll(bagKeyToTeams.get(bagKey));
                p.examples = bagKeyToExamples.get(bagKey);

                Set<String> uniqSeq = new HashSet<>();
                for(String occ : occs) {
                    List<String> w = occurrenceToWindow.get(occ);
                    if (uniqSeq.add(String.join("|", w))) p.observedSequences.add(w);
                }
                results.add(p);
            }
        }
        return results;
    }

    // Diğer pattern metodları (yer tutucu olarak, asıl odak dörtlüde)
    public List<DiscoveredPattern> discoverSerialScorePatterns(List<Match> matches) {
        // Seri skor mantığı buraya eklenebilir
        return new ArrayList<>();
    }
    public List<DiscoveredPattern> discoverRecurringScorePatterns(List<Match> matches) { return new ArrayList<>(); }
    public List<DiscoveredPattern> discoverReverseScorePatterns(List<Match> matches) { return new ArrayList<>(); }
    public List<DiscoveredPattern> discoverWeekBasedPatterns(List<Match> matches) { return new ArrayList<>(); }

    // ==========================================
    // 6. TAHMİN ALGORİTMASI (Düzeltilmiş)
    // ==========================================

    public void predictUpcomingWeek(List<Match> allMatches, List<DiscoveredPattern> patterns, String leagueUrl) {
        System.out.println("\n🔮🔮🔮 GELECEK HAFTA TAHMİNLERİ 🔮🔮🔮\n");
        try {
            // Son oynanan sezon ve haftayı bul
            String lastSeason = allMatches.stream().map(m -> m.season).max(Comparator.naturalOrder()).orElse("");

            driver.get(leagueUrl);
            Thread.sleep(4000);

            // Güncel fikstüre git
            try {
                WebElement fixBtn = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Fikstür")));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", fixBtn);
                Thread.sleep(4000);
            } catch (Exception e) {}

            List<WebElement> rows = driver.findElements(By.cssSelector("#dvFixtureInner table.list-table tbody tr"));
            System.out.println("Analiz edilen sezon: " + lastSeason + "\n");

            for (WebElement row : rows) {
                try {
                    if (row.getAttribute("class").contains("table-header")) continue;
                    List<WebElement> cells = row.findElements(By.tagName("td"));
                    if (cells.size() < 8) continue;

                    String date = cells.get(0).getText().trim();
                    String home = cells.get(3).getText().trim();
                    String away = cells.get(7).getText().trim();
                    String score = cells.get(5).getText().trim();

                    if (!home.isEmpty() && !away.isEmpty()) {
                        System.out.println("🆚 " + home + " vs " + away + " (" + date + ")");

                        // EĞER MAÇ OYNANDIYSA SKORU YAZDIR
                        if(score.contains("-")) System.out.println("   (Maç Oynandı: " + score + ")");

                        analyzeMatch(home, away, lastSeason, allMatches, patterns);
                        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    }
                } catch (Exception e) {}
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void analyzeMatch(String home, String away, String season, List<Match> allMatches, List<DiscoveredPattern> patterns) {
        for (String team : Arrays.asList(home, away)) {
            // Takımın bu sezondaki maçlarını çek ve TARİHE GÖRE SIRALA
            List<Match> history = allMatches.stream()
                    .filter(m -> (m.homeTeam.equals(team) || m.awayTeam.equals(team)) && m.season.equals(season))
                    .sorted(Comparator.comparing(Match::getLocalDate)) // Maastricht Fix
                    .collect(Collectors.toList());

            if (history.size() < 3) continue;

            List<Match> last3 = history.subList(history.size() - 3, history.size());
            List<String> last3Norm = last3.stream()
                    .map(m -> getSymmetricalScore(m, team))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (last3Norm.size() != 3) continue;

            for (DiscoveredPattern p : patterns) {
                if (!p.type.equals("DORTLU_CYCLE")) continue;

                // Bag Kontrolü
                String[] bag = p.bagKey.split("\\|");
                Map<String, Integer> pCounts = new HashMap<>();
                for (String s : bag) pCounts.put(s, pCounts.getOrDefault(s, 0) + 1);

                Map<String, Integer> tCounts = new HashMap<>();
                for (String s : last3Norm) tCounts.put(s, tCounts.getOrDefault(s, 0) + 1);

                boolean possible = true;
                for (String key : tCounts.keySet()) {
                    if (pCounts.getOrDefault(key, 0) < tCounts.get(key)) {
                        possible = false;
                        break;
                    }
                }

                if (possible) {
                    // Eksik olan skoru bul
                    Map<String, Integer> diff = new HashMap<>(pCounts);
                    for (String key : tCounts.keySet()) {
                        diff.put(key, diff.get(key) - tCounts.get(key));
                    }
                    List<String> missing = new ArrayList<>();
                    for (String k : diff.keySet()) {
                        for (int i = 0; i < diff.get(k); i++) missing.add(k);
                    }

                    if (missing.size() == 1) {
                        String predicted = missing.get(0);

                        // FİLTRELEME: BU TAKIM BU PATTERNİ DAHA ÖNCE EN AZ 2 KEZ YAŞADI MI?
                        long teamOccurrences = p.examples.stream()
                                .filter(ex -> ex.startsWith(team + " |"))
                                .count();

                        if (teamOccurrences >= 2) {
                            System.out.println("   🎯 " + team + " TAHMİN (DÖRTLÜ): Son 3 [" + String.join(", ", last3Norm) +
                                               "] → Beklenen: " + predicted +
                                               " | Pattern: " + p.bagKey +
                                               " | Takım Geçmişi: " + teamOccurrences + " kez");
                        }
                    }
                }
            }
        }
    }

    public void close() { if(driver!=null) driver.quit(); }

    public static void main(String[] args) {
        MackolikPatternDiscoveryVGoogle bot = new MackolikPatternDiscoveryVGoogle();
        Scanner scan = new Scanner(System.in);

        try {
            System.out.println("### MACKOLIK PATTERN DISCOVERY V5 (Fixed Date & Filter) ###");
            bot.showAvailableLeagues();
            String url = scan.nextLine();
            if (url.isEmpty()) url = "https://arsiv.mackolik.com/Puan-Durumu/1/TURKIYE-Super-Lig";

            Map<String, String> seasons = bot.getAvailableSeasonsMap(url);
            List<String> sIds = new ArrayList<>(seasons.values());

            System.out.println("Kaç sezon taransın? (Varsayılan 2)");
            int count = 2;
            try { count = Integer.parseInt(scan.nextLine()); } catch(Exception e){}

            List<String> selectedIds = sIds.subList(0, Math.min(count, sIds.size()));

            List<Match> matches = bot.scrapeMultipleSeasons(selectedIds, 40);
            System.out.println("Toplam Maç: " + matches.size());

            List<DiscoveredPattern> patterns = new ArrayList<>();
            patterns.addAll(bot.discoverQuartetCyclePatterns(matches));

            // Sıralama: Başarı oranı ve bulunma sayısı
            patterns.sort((a,b) -> {
                if (b.accuracy != a.accuracy) return Double.compare(b.accuracy, a.accuracy);
                return Integer.compare(b.foundCount, a.foundCount);
            });

            System.out.println("\nBULUNAN PATTERN SAYISI: " + patterns.size());
            patterns.forEach(System.out::println);

            bot.predictUpcomingWeek(matches, patterns, url);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bot.close();
            scan.close();
        }
    }
}