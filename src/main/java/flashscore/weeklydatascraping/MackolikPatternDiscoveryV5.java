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
import java.util.*;
import java.util.stream.Collectors;

public class MackolikPatternDiscoveryV5 {

    private WebDriver driver;
    private WebDriverWait wait;

    static class Match {
        String date;
        String homeTeam;
        String awayTeam;
        String htScore;
        String ftScore;
        int week;
        String season;

        int getHTHome() {
            try {
                return htScore == null ? -1 : Integer.parseInt(htScore.split("-")[0].trim());
            } catch (Exception e) {
                return -1;
            }
        }

        int getHTAway() {
            try {
                return htScore == null ? -1 : Integer.parseInt(htScore.split("-")[1].trim());
            } catch (Exception e) {
                return -1;
            }
        }

        int getFTHome() {
            try {
                return ftScore == null ? -1 : Integer.parseInt(ftScore.split("-")[0].trim());
            } catch (Exception e) {
                return -1;
            }
        }

        int getFTAway() {
            try {
                return ftScore == null ? -1 : Integer.parseInt(ftScore.split("-")[1].trim());
            } catch (Exception e) {
                return -1;
            }
        }

        String getHTFTResult() {
            if (htScore == null || ftScore == null) return "Unknown";
            try {
                int htH = getHTHome(), htA = getHTAway(), ftH = getFTHome(), ftA = getFTAway();
                if (htH == -1 || htA == -1 || ftH == -1 || ftA == -1) return "Unknown";

                String htR = htH > htA ? "1" : (htH < htA ? "2" : "X");
                String ftR = ftH > ftA ? "1" : (ftH < ftA ? "2" : "X");

                return htR + "/" + ftR;
            } catch (Exception e) {
                return "Unknown";
            }
        }

        String getHTFTCategory() {
            String htft = getHTFTResult();

            // REVERSE: 1/2 veya 2/1
            if (htft.equals("1/2") || htft.equals("2/1")) {
                return "REVERSE";
            }

            // DRAW_INVOLVED: 1/X veya 2/X
            if (htft.equals("1/X") || htft.equals("2/X")) {
                return "DRAW_INVOLVED";
            }

            // Diğer tüm durumlar IGNORE
            return "IGNORE";
        }

        boolean isHomeTeam(String team) {
            return homeTeam.equals(team);
        }

        boolean isAwayTeam(String team) {
            return awayTeam.equals(team);
        }

        String getOpponent(String team) {
            if (isHomeTeam(team)) return awayTeam;
            if (isAwayTeam(team)) return homeTeam;
            return null;
        }

        @Override
        public String toString() {
            return String.format("%s | Hafta %d | %s vs %s | HT:%s FT:%s | 🎯%s",
                    season, week, homeTeam, awayTeam, htScore, ftScore, getHTFTResult());
        }

        static String getNormalizedScore(String score) {
            if (score == null || !score.contains("-")) return null;
            String[] parts = score.split("-");
            try {
                int a = Integer.parseInt(parts[0].trim());
                int b = Integer.parseInt(parts[1].trim());
                return a + "-" + b;
            } catch (Exception e) {
                return null;
            }
        }
    }

    static class DiscoveredPattern {
        String type;
        String triggerValue;
        int gapMatches;
        String resultCategory;
        int foundCount;
        int totalChecked;
        double accuracy;
        List<String> examples;
        List<String> seasons;
        String priorMatch;
        String posteriorMatch;

        Map<Integer, Integer> reverseAfterGap = new HashMap<>();
        Map<Integer, Integer> drawInvolvedAfterGap = new HashMap<>();
        Map<Integer, Integer> totalAfterGap = new HashMap<>();

        List<List<String>> observedSequences = new ArrayList<>();
        String bagKey = "";

        DiscoveredPattern(String type, String trigger, int gap, String result) {
            this.type = type;
            this.triggerValue = trigger;
            this.gapMatches = gap;
            this.resultCategory = result;
            this.examples = new ArrayList<>();
            this.seasons = new ArrayList<>();
            this.priorMatch = "";
            this.posteriorMatch = "";
        }

        void calculate() {
            this.accuracy = totalChecked > 0 ? (double) foundCount / totalChecked * 100 : 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n").append("💎".repeat(50)).append("\n");

            if (type.equals("SERI_SKOR")) {
                sb.append("🔥 KEŞFEDİLEN SERİ SKOR PATTERN!\n");
                sb.append("📌 Tip: ").append(type).append("\n");
                sb.append("🎯 Seri: ").append(triggerValue).append("\n");
                sb.append("📊 Bulunma Sayısı: ").append(foundCount).append("\n");

                if (!priorMatch.isEmpty()) {
                    sb.append("🔙 ÖNCESİ: ").append(priorMatch).append("\n");
                }

                if (!posteriorMatch.isEmpty()) {
                    sb.append("🔜 SONRASI: ").append(posteriorMatch).append("\n");
                }

                if (!reverseAfterGap.isEmpty() || !drawInvolvedAfterGap.isEmpty()) {
                    sb.append("\n📈 SERİ SONRASI ANALİZ:\n");
                    List<Integer> gaps = new ArrayList<>(totalAfterGap.keySet());
                    Collections.sort(gaps);

                    for (int gap : gaps) {
                        int total = totalAfterGap.getOrDefault(gap, 0);
                        int reverse = reverseAfterGap.getOrDefault(gap, 0);
                        int drawInv = drawInvolvedAfterGap.getOrDefault(gap, 0);

                        if (total > 0) {
                            double reversePerc = (double) reverse / total * 100;
                            double drawPerc = (double) drawInv / total * 100;

                            String reverseEmoji = reversePerc >= 50 ? "🔥" : reversePerc > 0 ? "✅" : "❌";
                            String drawEmoji = drawPerc >= 50 ? "🔥" : drawPerc > 0 ? "✅" : "❌";

                            sb.append(String.format("   %d. maç sonra → %s 1/2-2/1: %d/%d (%.0f%%)  |  %s 1/X-2/X: %d/%d (%.0f%%)\n",
                                    gap, reverseEmoji, reverse, total, reversePerc, drawEmoji, drawInv, total, drawPerc));
                        }
                    }
                }

                if (!seasons.isEmpty()) {
                    sb.append("🏆 Sezonlar: ").append(String.join(", ", seasons)).append("\n");
                }
                if (!examples.isEmpty()) {
                    sb.append("\n🎲 ÖRNEKLER (Toplam ").append(examples.size()).append(" örnek):\n");
                    examples.forEach(ex -> sb.append("   ").append(ex).append("\n"));
                }

            } else if (type.equals("HAFTA_SKOR_TEKRAR")) {
                sb.append("🔥 KEŞFEDİLEN HAFTA-SKOR TEKRAR PATTERN!\n");
                sb.append("📌 Tip: ").append(type).append("\n");
                sb.append("📊 Skor Tekrarı: ").append(triggerValue).append("\n");
                sb.append("⏳ Hafta Arası Fark: ").append(gapMatches).append(" hafta\n");
                sb.append("✅ Tekrar Oranı: ").append(foundCount).append("/").append(totalChecked)
                        .append(" = ").append(String.format("%.1f%%", accuracy)).append("\n");

                if (!reverseAfterGap.isEmpty() || !drawInvolvedAfterGap.isEmpty()) {
                    sb.append("\n📈 PATTERN SONRASI ANALİZ:\n");
                    List<Integer> gaps = new ArrayList<>(totalAfterGap.keySet());
                    Collections.sort(gaps);

                    for (int gap : gaps) {
                        int total = totalAfterGap.getOrDefault(gap, 0);
                        int reverse = reverseAfterGap.getOrDefault(gap, 0);
                        int drawInv = drawInvolvedAfterGap.getOrDefault(gap, 0);

                        if (total > 0) {
                            double reversePerc = (double) reverse / total * 100;
                            double drawPerc = (double) drawInv / total * 100;

                            String reverseEmoji = reversePerc >= 50 ? "🔥" : reversePerc > 0 ? "✅" : "❌";
                            String drawEmoji = drawPerc >= 50 ? "🔥" : drawPerc > 0 ? "✅" : "❌";

                            sb.append(String.format("   %d. maç sonra → %s REVERSE: %d/%d (%.0f%%)  |  %s DRAW_INV: %d/%d (%.0f%%)\n",
                                    gap, reverseEmoji, reverse, total, reversePerc, drawEmoji, drawInv, total, drawPerc));
                        }
                    }
                }

                if (!seasons.isEmpty()) {
                    sb.append("🏆 Sezonlar: ").append(String.join(", ", seasons)).append("\n");
                }
                if (!examples.isEmpty()) {
                    sb.append("\n🎲 ÖRNEKLER (Toplam ").append(examples.size()).append(" örnek):\n");
                    examples.forEach(ex -> sb.append("   ").append(ex).append("\n"));
                }

            } else if (type.equals("TERS_SKOR")) {
                sb.append("🔥 KEŞFEDİLEN TERS SKOR PATTERN!\n");
                sb.append("📌 Tip: ").append(type).append("\n");
                sb.append("🎯 Takım: ").append(triggerValue).append("\n");
                sb.append("⏳ Gap: ").append(gapMatches).append(" maç sonra\n");
                sb.append("✅ Başarı: ").append(foundCount).append("/").append(totalChecked)
                        .append(" = ").append(String.format("%.1f%%", accuracy)).append("\n");

                if (!seasons.isEmpty()) {
                    sb.append("🏆 Sezonlar: ").append(String.join(", ", seasons)).append("\n");
                }

                if (!examples.isEmpty()) {
                    sb.append("\n🎲 ÖRNEKLER (Toplam ").append(examples.size()).append(" örnek):\n");
                    examples.forEach(ex -> sb.append("   ").append(ex).append("\n"));
                }

            } else if (type.equals("DORTLU_CYCLE")) {
                sb.append("🔥 KEŞFEDİLEN DÖRTLÜ ÇEVRİM PATTERN!\n");
                sb.append("📌 Tip: ").append(type).append("\n");
                sb.append("🎯 Pattern (Multiset): ").append(bagKey).append("\n");
                sb.append("📊 Tekrar Sayısı: ").append(foundCount).append(" kez (non-overlapping)\n");
                sb.append("✅ Başarı: ").append(String.format("%.1f%%", accuracy)).append("\n");

                if (!seasons.isEmpty()) {
                    sb.append("🏆 Sezonlar: ").append(String.join(", ", seasons)).append("\n");
                }

                if (!observedSequences.isEmpty()) {
                    sb.append("\n🔄 GÖZLENEN ROTASYONLAR:\n");
                    Set<String> uniqueRots = new HashSet<>();
                    for (List<String> seq : observedSequences) {
                        String rot = String.join(" → ", seq);
                        if (!uniqueRots.contains(rot)) {
                            uniqueRots.add(rot);
                            sb.append("   ").append(rot).append("\n");
                        }
                    }
                }

                if (!examples.isEmpty()) {
                    sb.append("\n🎲 ÖRNEKLER (Toplam ").append(examples.size()).append(" örnek):\n");
                    examples.forEach(ex -> sb.append("   ").append(ex).append("\n"));
                }

            } else {
                sb.append("🔥 KEŞFEDİLEN PATTERN!\n");
                sb.append("📌 Tip: ").append(type).append("\n");
                sb.append("🎯 Trigger: ").append(triggerValue).append("\n");
                sb.append("⏳ Gap: ").append(gapMatches).append(" maç sonra\n");
                sb.append("✅ Sonuç Kategori: ").append(getCategoryDescription()).append("\n");
                sb.append("📊 Başarı: ").append(foundCount).append("/").append(totalChecked)
                        .append(" = ").append(String.format("%.1f%%", accuracy)).append("\n");

                if (!seasons.isEmpty()) {
                    sb.append("🏆 Sezonlar: ").append(String.join(", ", seasons)).append("\n");
                }

                if (!examples.isEmpty()) {
                    sb.append("\n🎲 ÖRNEKLER (Toplam ").append(examples.size()).append(" örnek):\n");
                    examples.forEach(ex -> sb.append("   ").append(ex).append("\n"));
                }
            }
            sb.append("💎".repeat(50)).append("\n");
            return sb.toString();
        }

        private String getCategoryDescription() {
            switch (resultCategory) {
                case "REVERSE":
                    return "1/2-2/1 - İlk yarı ve maç sonu ters";
                case "DRAW_INVOLVED":
                    return "1/X-2/X - Beraberlik karışık";
                case "TERS_SKOR":
                    return "TERS SKOR PATTERN'İ";
                default:
                    if (resultCategory.contains("-")) {
                        return "SKOR TAHMİNİ: " + resultCategory;
                    }
                    return resultCategory;
            }
        }
    }

    // Yardımcı class - WindowInfo
    static class WindowInfo {
        int startIndex;
        String bagKey;
        List<String> window;

        WindowInfo(int startIndex, String bagKey, List<String> window) {
            this.startIndex = startIndex;
            this.bagKey = bagKey;
            this.window = window;
        }
    }

    public MackolikPatternDiscoveryV5() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    public void showAvailableLeagues() {
        System.out.println("🏆 MEVCUT LİGLER:\n");
        Map<String, String> leagues = new LinkedHashMap<>();
        int index = 1;
        for (Map.Entry<String, String> entry : leagues.entrySet()) {
            System.out.println(index + ". " + entry.getKey());
            System.out.println("   URL: " + entry.getValue());
            index++;
        }
        System.out.println("\n💡 Lig URL'sini kopyalayıp yapıştırın");
    }

    public Map<String, String> getAvailableSeasonsMap(String leagueUrl) {
        System.out.println("🔍 Sezonlar yükleniyor...");

        Map<String, String> seasonMap = new LinkedHashMap<>();

        try {
            driver.get(leagueUrl);
            Thread.sleep(4000);

            WebElement seasonSelectElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cboSeason")));
            Select seasonSelect = new Select(seasonSelectElement);

            List<WebElement> seasonOptions = seasonSelect.getOptions();

            System.out.println("📅 MEVCUT SEZONLAR:");
            for (int i = 0; i < Math.min(15, seasonOptions.size()); i++) {
                WebElement option = seasonOptions.get(i);
                String seasonText = option.getText().trim();
                String seasonValue = option.getAttribute("value");

                if (!seasonText.isEmpty() && !seasonValue.isEmpty()) {
                    seasonMap.put(seasonText, seasonValue);
                    System.out.println((i+1) + ". " + seasonText + " (ID: " + seasonValue + ")");
                }
            }

            return seasonMap;

        } catch (Exception e) {
            System.out.println("❌ Sezonlar yüklenirken hata: " + e.getMessage());
            return seasonMap;
        }
    }

    public List<Match> scrapeMultipleSeasons(List<String> seasonIds, int maxWeeksPerSeason) {
        List<Match> allMatches = new ArrayList<>();

        for (String seasonId : seasonIds) {
            System.out.println("\n" + "🔥".repeat(40));
            System.out.println("🎯 SEZON ID: " + seasonId + " YÜKLENİYOR...");
            System.out.println("🔥".repeat(40) + "\n");

            try {
                String seasonUrl = "https://arsiv.mackolik.com/Standings/Default.aspx?sId=" + seasonId;
                driver.get(seasonUrl);
                Thread.sleep(5000);

                WebElement seasonSelectElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cboSeason")));
                Select seasonSelect = new Select(seasonSelectElement);
                String currentSeason = seasonSelect.getFirstSelectedOption().getText().trim();
                System.out.println("✅ Sezon yüklendi: " + currentSeason);

                WebElement fiksturLink = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Fikstür")));
                fiksturLink.click();
                Thread.sleep(5000);

                WebElement firstButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("span.first")));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", firstButton);
                Thread.sleep(4000);
                System.out.println("✅ İlk haftaya gidildi!\n");

                int weekCounter = 0;
                int seasonMatchCount = 0;
                int emptyWeekCount = 0;

                while (weekCounter < maxWeeksPerSeason && emptyWeekCount < 2) {
                    weekCounter++;

                    try {
                        WebElement weekSelectElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cboWeek")));
                        Select weekSelect = new Select(weekSelectElement);
                        String currentWeek = weekSelect.getFirstSelectedOption().getText();
                        System.out.println("📅 " + currentWeek + " işleniyor...");

                        WebElement table = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#dvFixtureInner table.list-table")));
                        List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));

                        int matchesInWeek = 0;

                        for (WebElement row : rows) {
                            try {
                                if (row.getAttribute("class") != null && row.getAttribute("class").contains("table-header")) {
                                    continue;
                                }

                                List<WebElement> cells = row.findElements(By.tagName("td"));
                                if (cells.size() < 9) continue;

                                Match match = new Match();
                                match.week = weekCounter;
                                match.season = currentSeason;
                                match.date = cells.get(0).getText().trim();
                                match.homeTeam = cells.get(3).getText().trim();
                                match.awayTeam = cells.get(7).getText().trim();

                                String ftScore = cells.get(5).getText().trim();
                                String htScore = cells.get(8).getText().trim();

                                if (!ftScore.isEmpty() && !htScore.isEmpty() &&
                                        ftScore.contains("-") && htScore.contains("-") &&
                                        !ftScore.equals("-") && !htScore.equals("-") &&
                                        !ftScore.equals("v")) {
                                    match.ftScore = ftScore;
                                    match.htScore = htScore;
                                    allMatches.add(match);
                                    matchesInWeek++;
                                    seasonMatchCount++;
                                }

                            } catch (Exception e) {
                                // Skip row parse error
                            }
                        }

                        System.out.println("   ✅ " + matchesInWeek + " maç (Sezon Toplam: " + seasonMatchCount + " | Genel Toplam: " + allMatches.size() + ")");

                        if (matchesInWeek == 0) {
                            emptyWeekCount++;
                            if (emptyWeekCount >= 2) {
                                System.out.println("   ⚠️ Üst üste 2 boş hafta, sezon tamamlandı!");
                                break;
                            }
                        } else {
                            emptyWeekCount = 0;
                        }

                        try {
                            WebElement nextButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("span.next")));
                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButton);
                            Thread.sleep(4000);
                        } catch (Exception e) {
                            System.out.println("   ✅ Sezon sonu!");
                            break;
                        }

                    } catch (Exception e) {
                        System.out.println("   ⚠️ Hafta okuma hatası: " + e.getMessage());
                        break;
                    }
                }

                System.out.println("\n✅ SEZON " + currentSeason + " TAMAMLANDI: " + seasonMatchCount + " maç\n");

            } catch (Exception e) {
                System.out.println("❌ Sezon " + seasonId + " hatası: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("\n🏆🏆🏆 TÜM SEZONLAR TAMAMLANDI! 🏆🏆🏆");
        System.out.println("📊 TOPLAM " + allMatches.size() + " MAÇ YÜKLENDİ!\n");

        return allMatches;
    }

    private Map<String, List<Match>> groupByTeamAllSeasons(List<Match> matches) {
        Map<String, List<Match>> map = new HashMap<>();

        for (Match m : matches) {
            if (m.homeTeam != null && !m.homeTeam.isEmpty()) {
                map.putIfAbsent(m.homeTeam, new ArrayList<>());
                map.get(m.homeTeam).add(m);
            }
            if (m.awayTeam != null && !m.awayTeam.isEmpty()) {
                map.putIfAbsent(m.awayTeam, new ArrayList<>());
                map.get(m.awayTeam).add(m);
            }
        }

        for (var entry : map.entrySet()) {
            entry.getValue().sort(Comparator
                    .comparing((Match m) -> m.season)
                    .thenComparing((Match m) -> parseDate(m.date)));
        }

        return map;
    }

    private static java.time.LocalDate parseDate(String date) {
        try {
            String[] parts = date.split("/");
            if (parts.length >= 2) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                return java.time.LocalDate.of(2024, month, day);
            }
        } catch (Exception e) {
            // Hata olursa bugünü döndür
        }
        return java.time.LocalDate.now();
    }

    // TERS SKORLAR AYNI: 0-1 = 1-0, 3-1 = 1-3 (küçük sayı her zaman önce)
    private static String getSymmetricalScore(Match m, String team) {
        if (m.ftScore == null || !m.ftScore.contains("-")) return null;
        String[] parts = m.ftScore.split("-");
        try {
            int home = Integer.parseInt(parts[0].trim());
            int away = Integer.parseInt(parts[1].trim());

            // Takımın skorunu bul
            int teamScore, oppScore;
            if (m.isHomeTeam(team)) {
                teamScore = home;
                oppScore = away;
            } else if (m.isAwayTeam(team)) {
                teamScore = away;
                oppScore = home;
            } else {
                return null;
            }

            // SYMMETRICAL: Küçük sayıyı önce yaz
            // Böylece 0-1 = 1-0, 3-1 = 1-3 olur
            int min = Math.min(teamScore, oppScore);
            int max = Math.max(teamScore, oppScore);
            return min + "-" + max;

        } catch (Exception e) {
            return null;
        }
    }

    public List<DiscoveredPattern> discoverRecurringScorePatterns(List<Match> matches) {
        System.out.println("🔍 HAFTA+SKOR + HAFTA+SKOR PATTERN'LERİ ARANIYOR (ÇOK SEZONLU)...\n");

        List<DiscoveredPattern> discovered = new ArrayList<>();
        Map<String, Map<String, Map<Integer, Match>>> teamSeasonWeeks = new HashMap<>();

        for (Match m : matches) {
            teamSeasonWeeks.computeIfAbsent(m.homeTeam, k -> new HashMap<>())
                    .computeIfAbsent(m.season, k -> new HashMap<>())
                    .put(m.week, m);
            teamSeasonWeeks.computeIfAbsent(m.awayTeam, k -> new HashMap<>())
                    .computeIfAbsent(m.season, k -> new HashMap<>())
                    .put(m.week, m);
        }

        Map<String, List<Match>> teamMatches = groupByTeamAllSeasons(matches);

        for (String team : teamSeasonWeeks.keySet()) {
            Map<String, Map<Integer, Match>> seasons = teamSeasonWeeks.get(team);

            if (seasons.size() < 2) continue;

            Set<Integer> allWeeks = new HashSet<>();
            for (var s : seasons.values()) {
                allWeeks.addAll(s.keySet());
            }
            List<Integer> sortedWeeks = new ArrayList<>(allWeeks);
            Collections.sort(sortedWeeks);

            for (int weekIdxA = 0; weekIdxA < sortedWeeks.size(); weekIdxA++) {
                int weekA = sortedWeeks.get(weekIdxA);

                for (int weekIdxB = weekIdxA + 1; weekIdxB < sortedWeeks.size(); weekIdxB++) {
                    int weekB = sortedWeeks.get(weekIdxB);
                    int gapWeeks = weekB - weekA;

                    if (gapWeeks > 15) continue;

                    Map<String, List<String>> scoreAtoSeasonsHavingIt = new HashMap<>();
                    for (String season : seasons.keySet()) {
                        Match mA = seasons.get(season).get(weekA);
                        if (mA != null && mA.ftScore != null && !mA.ftScore.equals("Unknown")) {
                            scoreAtoSeasonsHavingIt.computeIfAbsent(mA.ftScore, k -> new ArrayList<>()).add(season);
                        }
                    }

                    for (String scoreA : scoreAtoSeasonsHavingIt.keySet()) {
                        List<String> seasonsWithScoreA = scoreAtoSeasonsHavingIt.get(scoreA);
                        if (seasonsWithScoreA.size() < 2) continue;

                        Map<String, Integer> scoreBCounts = new HashMap<>();
                        Map<String, List<String>> scoreBtoExamples = new HashMap<>();

                        for (String season : seasonsWithScoreA) {
                            Match mB = seasons.get(season).get(weekB);
                            if (mB != null && mB.ftScore != null && !mB.ftScore.equals("Unknown")) {
                                String scoreB = mB.ftScore;
                                scoreBCounts.put(scoreB, scoreBCounts.getOrDefault(scoreB, 0) + 1);

                                String example = String.format("%s | Sezon: %s\n      → Hafta %d (%s): %s vs %s = %s\n      → Hafta %d (%s): %s vs %s = %s",
                                        team, season,
                                        weekA, seasons.get(season).get(weekA).date,
                                        seasons.get(season).get(weekA).homeTeam, seasons.get(season).get(weekA).awayTeam, scoreA,
                                        weekB, mB.date, mB.homeTeam, mB.awayTeam, scoreB);
                                scoreBtoExamples.computeIfAbsent(scoreB, k -> new ArrayList<>()).add(example);
                            }
                        }

                        for (String scoreB : scoreBCounts.keySet()) {
                            int count = scoreBCounts.get(scoreB);
                            int totalChecked = seasonsWithScoreA.size();

                            if (count >= 2 && (double) count / totalChecked >= 0.4) {
                                DiscoveredPattern pattern = new DiscoveredPattern(
                                        "HAFTA_SKOR_TEKRAR",
                                        "W" + weekA + ": " + scoreA + " → W" + weekB + ": " + scoreB,
                                        gapWeeks,
                                        ""
                                );
                                pattern.foundCount = count;
                                pattern.totalChecked = totalChecked;
                                pattern.calculate();
                                pattern.examples = scoreBtoExamples.get(scoreB);
                                pattern.seasons = seasonsWithScoreA;

                                List<Match> teamAllMatches = teamMatches.get(team);
                                if (teamAllMatches != null) {
                                    for (String season : seasonsWithScoreA) {
                                        Match mB = seasons.get(season).get(weekB);
                                        if (mB != null) {
                                            int mBIndex = teamAllMatches.indexOf(mB);
                                            if (mBIndex >= 0 && mBIndex < teamAllMatches.size() - 1) {
                                                int endIndex = Math.min(mBIndex + 6, teamAllMatches.size());
                                                for (int k = mBIndex + 1; k < endIndex; k++) {
                                                    Match followingMatch = teamAllMatches.get(k);
                                                    int gapNumber = k - mBIndex;

                                                    String category = followingMatch.getHTFTCategory();
                                                    pattern.totalAfterGap.put(gapNumber, pattern.totalAfterGap.getOrDefault(gapNumber, 0) + 1);

                                                    if (category.equals("REVERSE")) {
                                                        pattern.reverseAfterGap.put(gapNumber, pattern.reverseAfterGap.getOrDefault(gapNumber, 0) + 1);
                                                    } else if (category.equals("DRAW_INVOLVED")) {
                                                        pattern.drawInvolvedAfterGap.put(gapNumber, pattern.drawInvolvedAfterGap.getOrDefault(gapNumber, 0) + 1);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                discovered.add(pattern);
                            }
                        }
                    }
                }
            }
        }

        System.out.println("✅ " + discovered.size() + " hafta-skor tekrar pattern bulundu!\n");
        return discovered;
    }

    public List<DiscoveredPattern> discoverReverseScorePatterns(List<Match> matches) {
        System.out.println("🔍 TERS SKOR PATTERN'LERİ ARANIYOR (ÇOK SEZONLU)...\n");

        List<DiscoveredPattern> discovered = new ArrayList<>();
        Map<String, List<Match>> teamMatches = groupByTeamAllSeasons(matches);

        for (String team : teamMatches.keySet()) {
            List<Match> games = teamMatches.get(team);

            for (int gap = 1; gap <= 5; gap++) {
                DiscoveredPattern pattern = new DiscoveredPattern(
                        "TERS_SKOR",
                        team,
                        gap,
                        "TERS_SKOR"
                );

                for (int i = 0; i < games.size() - gap; i++) {
                    Match current = games.get(i);
                    String score = current.ftScore;
                    if (score == null || !score.contains("-")) continue;
                    String[] parts = score.split("-");
                    String a = parts[0].trim();
                    String b = parts[1].trim();
                    if (a.equals(b)) continue;
                    String reverse = b + "-" + a;

                    if (i + gap >= games.size()) continue;
                    Match resultMatch = games.get(i + gap);

                    if (resultMatch == null || resultMatch.ftScore == null) continue;

                    pattern.totalChecked++;
                    if (resultMatch.ftScore.equals(reverse)) {
                        pattern.foundCount++;
                        String example = current + " (" + score + ") → " + gap + " maç sonra → " + resultMatch + " (" + reverse + ")";
                        pattern.examples.add(example);
                        if (!pattern.seasons.contains(current.season)) {
                            pattern.seasons.add(current.season);
                        }
                    }
                }

                pattern.calculate();
                if (pattern.totalChecked >= 5 && pattern.accuracy >= 30 && pattern.seasons.size() >= 2) {
                    discovered.add(pattern);
                }
            }
        }

        System.out.println("✅ " + discovered.size() + " ters skor pattern bulundu!\n");
        return discovered;
    }

    public List<DiscoveredPattern> discoverSerialScorePatterns(List<Match> matches) {
        System.out.println("🔍 SERİ SKOR PATTERN'LERİ ARANIYOR (ÇOK SEZONLU)...\n");

        Map<String, Integer> seqCounts = new HashMap<>();
        Map<String, List<String>> seqSeasons = new HashMap<>();
        Map<String, List<String>> seqExamples = new HashMap<>();
        Map<String, String> seqPrior = new HashMap<>();
        Map<String, String> seqPosterior = new HashMap<>();
        Map<String, List<List<Match>>> seqFollowingMatches = new HashMap<>();

        Map<String, List<Match>> teamMatches = groupByTeamAllSeasons(matches);

        for (String team : teamMatches.keySet()) {
            List<Match> games = teamMatches.get(team);
            List<String> scores = new ArrayList<>();
            List<String> seasonsList = new ArrayList<>();

            for (Match m : games) {
                String norm = Match.getNormalizedScore(m.ftScore);
                if (norm != null) {
                    scores.add(norm);
                    seasonsList.add(m.season);
                }
            }

            for (int len = 3; len <= 4; len++) {
                for (int i = 0; i < scores.size() - len + 1; i++) {
                    List<String> seq = scores.subList(i, i + len);
                    String key = String.join(" + ", seq);
                    seqCounts.put(key, seqCounts.getOrDefault(key, 0) + 1);

                    List<String> currentSeasons = seqSeasons.computeIfAbsent(key, k -> new ArrayList<>());
                    Set<String> seqSet = new HashSet<>(currentSeasons);
                    seqSet.add(seasonsList.get(i));
                    seqSeasons.put(key, new ArrayList<>(seqSet));

                    StringBuilder ex = new StringBuilder(team + ": ");
                    for (int j = 0; j < len; j++) {
                        ex.append(games.get(i + j).toString()).append(" + ");
                    }
                    seqExamples.computeIfAbsent(key, k -> new ArrayList<>()).add(ex.toString().trim());

                    if (i > 0 && !seqPrior.containsKey(key)) {
                        Match priorMatch = games.get(i - 1);
                        seqPrior.put(key, priorMatch.ftScore);
                    }

                    if (i + len < games.size() && !seqPosterior.containsKey(key)) {
                        Match posteriorMatch = games.get(i + len);
                        seqPosterior.put(key, posteriorMatch.ftScore);
                    }

                    if (i + len < games.size()) {
                        List<Match> followingMatches = games.subList(
                                i + len,
                                Math.min(i + len + 5, games.size())
                        );
                        seqFollowingMatches.computeIfAbsent(key, k -> new ArrayList<>()).add(followingMatches);
                    }
                }
            }
        }

        List<DiscoveredPattern> discovered = new ArrayList<>();
        for (String key : seqCounts.keySet()) {
            int count = seqCounts.get(key);
            List<String> seas = seqSeasons.get(key);
            if (count >= 3 && seas.size() >= 2) {
                DiscoveredPattern pattern = new DiscoveredPattern("SERI_SKOR", key, 0, "");
                pattern.foundCount = count;
                pattern.totalChecked = count;
                pattern.calculate();
                pattern.seasons = seas;
                pattern.examples = seqExamples.get(key);

                if (seqPrior.containsKey(key)) {
                    pattern.priorMatch = "Seri öncesi: " + seqPrior.get(key);
                }
                if (seqPosterior.containsKey(key)) {
                    pattern.posteriorMatch = "Seri sonrası: " + seqPosterior.get(key);
                }

                List<List<Match>> allFollowing = seqFollowingMatches.get(key);
                if (allFollowing != null) {
                    for (List<Match> followingMatches : allFollowing) {
                        for (int i = 0; i < followingMatches.size() && i < 5; i++) {
                            Match nextMatch = followingMatches.get(i);
                            String category = nextMatch.getHTFTCategory();

                            int matchNumber = i + 1;
                            pattern.totalAfterGap.put(matchNumber, pattern.totalAfterGap.getOrDefault(matchNumber, 0) + 1);

                            if (category.equals("REVERSE")) {
                                pattern.reverseAfterGap.put(matchNumber, pattern.reverseAfterGap.getOrDefault(matchNumber, 0) + 1);
                            } else if (category.equals("DRAW_INVOLVED")) {
                                pattern.drawInvolvedAfterGap.put(matchNumber, pattern.drawInvolvedAfterGap.getOrDefault(matchNumber, 0) + 1);
                            }
                        }
                    }
                }

                discovered.add(pattern);
            }
        }

        System.out.println("✅ " + discovered.size() + " seri skor pattern bulundu!\n");
        return discovered;
    }

    public List<DiscoveredPattern> discoverQuartetCyclePatterns(List<Match> matches) {
        System.out.println("🔍 DÖRTLÜ ÇEVRİM (4-lü) PATTERN'LERİ ARANIYOR - TERS SKORLAR VE SIRA FARKETMEZ (ÇOK SEZONLU)...\n");

        Map<String, List<String>> bagKeyToOccurrences = new HashMap<>();
        Map<String, List<String>> bagKeyToExamples = new HashMap<>();
        Map<String, Set<String>> bagKeyToSeasons = new HashMap<>();

        Map<String, List<String>> occurrenceToWindow = new HashMap<>();

        Map<String, List<Match>> teamMatches = groupByTeamAllSeasons(matches);

        for (String team : teamMatches.keySet()) {
            List<Match> games = teamMatches.get(team);
            List<String> normScores = new ArrayList<>();
            List<String> seasonsList = new ArrayList<>();

            for (Match m : games) {
                String norm = getSymmetricalScore(m, team);
                normScores.add(norm);
                seasonsList.add(m.season);
            }

            Map<String, List<Integer>> seasonToIndices = new HashMap<>();
            for (int i = 0; i < seasonsList.size(); i++) {
                String season = seasonsList.get(i);
                seasonToIndices.computeIfAbsent(season, k -> new ArrayList<>()).add(i);
            }

            for (String season : seasonToIndices.keySet()) {
                List<Integer> indices = seasonToIndices.get(season);

                List<WindowInfo> windows = new ArrayList<>();

                for (int idx : indices) {
                    if (idx + 4 <= normScores.size()) {
                        List<String> window = normScores.subList(idx, idx + 4);
                        if (window.stream().anyMatch(Objects::isNull)) continue;

                        List<String> sorted = new ArrayList<>(window);
                        Collections.sort(sorted);
                        String bagKey = String.join("|", sorted);

                        windows.add(new WindowInfo(idx, bagKey, new ArrayList<>(window)));
                    }
                }

                Map<String, List<WindowInfo>> bagKeyToWindows = new HashMap<>();
                for (WindowInfo wi : windows) {
                    bagKeyToWindows.computeIfAbsent(wi.bagKey, k -> new ArrayList<>()).add(wi);
                }

                for (String bagKey : bagKeyToWindows.keySet()) {
                    List<WindowInfo> candidateWindows = bagKeyToWindows.get(bagKey);

                    candidateWindows.sort(Comparator.comparingInt(w -> w.startIndex));

                    List<WindowInfo> selected = new ArrayList<>();
                    int lastEnd = -1;

                    for (WindowInfo wi : candidateWindows) {
                        if (wi.startIndex >= lastEnd) {
                            selected.add(wi);
                            lastEnd = wi.startIndex + 4;
                        }
                    }

                    for (WindowInfo wi : selected) {
                        String occurrenceKey = team + "|" + season + "|" + wi.startIndex;
                        bagKeyToOccurrences.computeIfAbsent(bagKey, k -> new ArrayList<>()).add(occurrenceKey);
                        occurrenceToWindow.put(occurrenceKey, wi.window);
                        bagKeyToSeasons.computeIfAbsent(bagKey, k -> new HashSet<>()).add(season);

                        // YENİ: Detaylı örnek - her maçın tarih, HT ve FT skorlarını göster
                        StringBuilder detailedExample = new StringBuilder();
                        detailedExample.append(team).append(" | Sezon: ").append(season)
                                .append(" | Başlangıç İndeks: ").append(wi.startIndex).append("\n");

                        // Her 4 maçın detayını ekle
                        for (int matchIdx = 0; matchIdx < 4 && (wi.startIndex + matchIdx) < games.size(); matchIdx++) {
                            Match m = games.get(wi.startIndex + matchIdx);
                            String symScore = getSymmetricalScore(m, team);
                            detailedExample.append(String.format("      %d. Maç (%s): %s vs %s | HT: %s | FT: %s | Sym: %s\n",
                                    matchIdx + 1,
                                    m.date,
                                    m.homeTeam,
                                    m.awayTeam,
                                    m.htScore != null ? m.htScore : "?",
                                    m.ftScore != null ? m.ftScore : "?",
                                    symScore != null ? symScore : "?"));
                        }

                        bagKeyToExamples.computeIfAbsent(bagKey, k -> new ArrayList<>()).add(detailedExample.toString().trim());

                        System.out.println("[DEBUG] NON-OVERLAP eklendi: team=" + team + " season=" + season +
                                " startIdx=" + wi.startIndex + " window=" + String.join(", ", wi.window) + " bagKey=" + bagKey);
                    }
                }
            }
        }

        List<DiscoveredPattern> discovered = new ArrayList<>();

        for (String bagKey : bagKeyToOccurrences.keySet()) {
            List<String> occurrences = bagKeyToOccurrences.get(bagKey);
            Set<String> seasons = bagKeyToSeasons.getOrDefault(bagKey, Collections.emptySet());

            if (occurrences.size() >= 2) {
                DiscoveredPattern pattern = new DiscoveredPattern("DORTLU_CYCLE", bagKey, 0, "");
                pattern.bagKey = bagKey;
                pattern.foundCount = occurrences.size();
                pattern.totalChecked = occurrences.size();
                pattern.calculate();
                pattern.seasons = new ArrayList<>(seasons);
                pattern.examples = bagKeyToExamples.getOrDefault(bagKey, new ArrayList<>());

                Set<String> uniqueSeqs = new HashSet<>();
                pattern.observedSequences = new ArrayList<>();

                for (String occ : occurrences) {
                    List<String> window = occurrenceToWindow.get(occ);
                    String rep = String.join("|", window);
                    if (!uniqueSeqs.contains(rep)) {
                        uniqueSeqs.add(rep);
                        pattern.observedSequences.add(new ArrayList<>(window));
                    }
                }

                System.out.println("[DEBUG] PATTERN KABUL! bagKey=" + bagKey +
                        " gerçek_tekrar=" + occurrences.size() +
                        " sezonlar=" + seasons.size() +
                        " occurrences=" + String.join(", ", occurrences));

                discovered.add(pattern);
            } else {
                System.out.println("[DEBUG] PATTERN RED! bagKey=" + bagKey +
                        " gerçek_tekrar=" + occurrences.size() + " (gereken: >=2)" +
                        " occurrences=" + String.join(", ", occurrences));
            }
        }

        System.out.println("✅ " + discovered.size() + " dörtlü cycle pattern bulundu!\n");
        return discovered;
    }

    public List<DiscoveredPattern> discoverWeekBasedPatterns(List<Match> matches) {
        System.out.println("🔍 HAFTA BAZLI PATTERN'LERİ ARANIYOR (ÇOK SEZONLU)...\n");

        Map<String, Map<String, Map<Integer, Match>>> teamSeasonWeeks = new HashMap<>();
        for (Match m : matches) {
            teamSeasonWeeks.computeIfAbsent(m.homeTeam, k -> new HashMap<>())
                    .computeIfAbsent(m.season, k -> new HashMap<>())
                    .put(m.week, m);
            teamSeasonWeeks.computeIfAbsent(m.awayTeam, k -> new HashMap<>())
                    .computeIfAbsent(m.season, k -> new HashMap<>())
                    .put(m.week, m);
        }

        List<DiscoveredPattern> discovered = new ArrayList<>();

        for (String team : teamSeasonWeeks.keySet()) {
            Map<String, Map<Integer, Match>> seasons = teamSeasonWeeks.get(team);
            if (seasons.size() < 2) continue;

            Set<Integer> allWeeks = new HashSet<>();
            for (var s : seasons.values()) {
                allWeeks.addAll(s.keySet());
            }
            List<Integer> weeks = new ArrayList<>(allWeeks);
            Collections.sort(weeks);

            for (int idx = 0; idx < weeks.size(); idx++) {
                int weekN = weeks.get(idx);
                for (int gap = 1; gap <= 3; gap++) {
                    int weekK = weekN + gap;
                    if (!allWeeks.contains(weekK)) continue;

                    Map<String, List<String>> scoreToSeasons = new HashMap<>();
                    for (String season : seasons.keySet()) {
                        Match mN = seasons.get(season).get(weekN);
                        if (mN != null && mN.ftScore != null && !mN.ftScore.equals("Unknown")) {
                            scoreToSeasons.computeIfAbsent(mN.ftScore, k -> new ArrayList<>()).add(season);
                        }
                    }

                    for (String triggerScore : scoreToSeasons.keySet()) {
                        List<String> trigSeasons = scoreToSeasons.get(triggerScore);
                        if (trigSeasons.size() < 2) continue;

                        Map<String, Integer> outcomeCounts = new HashMap<>();
                        int total = 0;
                        List<String> examples = new ArrayList<>();

                        for (String season : trigSeasons) {
                            Match mK = seasons.get(season).get(weekK);
                            if (mK != null && !mK.getHTFTCategory().equals("IGNORE")) {
                                total++;

                                String category = mK.getHTFTCategory();
                                outcomeCounts.put(category, outcomeCounts.getOrDefault(category, 0) + 1);
                                examples.add(seasons.get(season).get(weekN).toString() + " → " + mK.toString());
                            }
                        }

                        if (total < 2) continue;

                        String bestOutcome = null;
                        int maxCount = 0;
                        for (var entry : outcomeCounts.entrySet()) {
                            if (entry.getValue() > maxCount) {
                                maxCount = entry.getValue();
                                bestOutcome = entry.getKey();
                            }
                        }

                        double acc = (double) maxCount / total * 100;
                        if (acc >= 70 && maxCount >= 2) {
                            DiscoveredPattern pattern = new DiscoveredPattern(
                                    "HAFTA_BAZLI",
                                    team + " Hafta " + weekN + " skor " + triggerScore,
                                    gap,
                                    bestOutcome
                            );
                            pattern.foundCount = maxCount;
                            pattern.totalChecked = total;
                            pattern.accuracy = acc;
                            pattern.seasons.addAll(trigSeasons);
                            pattern.examples = examples;
                            discovered.add(pattern);
                        }
                    }
                }
            }
        }

        System.out.println("✅ " + discovered.size() + " hafta bazlı pattern bulundu!\n");
        return discovered;
    }

    private static void showPatternStatistics(List<DiscoveredPattern> patterns) {
        System.out.println("\n📈 PATTERN İSTATİSTİKLERİ:");

        if (patterns.isEmpty()) {
            System.out.println("❌ İstatistik hesaplanacak pattern bulunamadı!");
            return;
        }

        double maxAccuracy = patterns.stream().mapToDouble(p -> p.accuracy).max().orElse(0);
        double avgAccuracy = patterns.stream().mapToDouble(p -> p.accuracy).average().orElse(0);

        System.out.println("✅ En Yüksek Doğruluk: %" + String.format("%.1f", maxAccuracy));
        System.out.println("📊 Ortalama Doğruluk: %" + String.format("%.1f", avgAccuracy));
        System.out.println("🎯 Toplam Pattern: " + patterns.size());

        Map<String, Long> typeDistribution = patterns.stream()
                .collect(Collectors.groupingBy(p -> p.type, Collectors.counting()));

        System.out.println("🏷️  Tip Dağılımı: " + typeDistribution);

        long highAccuracy = patterns.stream().filter(p -> p.accuracy >= 70).count();
        long mediumAccuracy = patterns.stream().filter(p -> p.accuracy >= 50 && p.accuracy < 70).count();
        long lowAccuracy = patterns.stream().filter(p -> p.accuracy >= 30 && p.accuracy < 50).count();

        System.out.println("\n🎖️  Başarı Dağılımı:");
        System.out.println("   🥇 %70+ Başarı: " + highAccuracy + " pattern");
        System.out.println("   🥈 %50-70 Başarı: " + mediumAccuracy + " pattern");
        System.out.println("   🥉 %30-50 Başarı: " + lowAccuracy + " pattern");
    }

    public void close() {
        if (driver != null) driver.quit();
    }

    public void predictUpcomingWeek(List<Match> allMatches, List<DiscoveredPattern> patterns, String leagueUrl) {
        System.out.println("\n🔮🔮🔮 GELECEK HAFTA TAHMİNLERİ 🔮🔮🔮\n");

        try {
            String lastSeason = allMatches.stream()
                    .map(m -> m.season)
                    .max(Comparator.naturalOrder())
                    .orElse("");

            int lastPlayedWeek = allMatches.stream()
                    .filter(m -> m.season.equals(lastSeason))
                    .mapToInt(m -> m.week)
                    .max()
                    .orElse(0);

            System.out.println("📅 Son Sezon: " + lastSeason);
            System.out.println("⚽ Son Oynanan Hafta: " + lastPlayedWeek);
            System.out.println("⏳ Fikstür yükleniyor...\n");

            driver.get(leagueUrl);
            Thread.sleep(4000);

            try {
                WebElement seasonSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cboSeason")));
                Select select = new Select(seasonSelect);
                select.selectByIndex(0);
                System.out.println("✅ Güncel sezon seçildi");
                Thread.sleep(3000);
            } catch (Exception e) {
                System.out.println("❌ Sezon seçimi hatası: " + e.getMessage());
                return;
            }

            try {
                WebElement fiksturLink = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Fikstür")));
                fiksturLink.click();
                Thread.sleep(5000);
                System.out.println("✅ Fikstür sayfası açıldı");
            } catch (Exception e) {
                System.out.println("❌ Fikstür sayfası açılamadı: " + e.getMessage());
                return;
            }

            int currentWeek = lastPlayedWeek + 1;
            try {
                WebElement weekSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cboWeek")));
                Select weekSelectObj = new Select(weekSelect);
                String selectedWeek = weekSelectObj.getFirstSelectedOption().getText();
                System.out.println("📋 Gösterilen hafta: " + selectedWeek);

                if (selectedWeek.matches(".*\\d+.*")) {
                    String numOnly = selectedWeek.replaceAll("\\D+", " ").trim().split(" ")[0];
                    try {
                        currentWeek = Integer.parseInt(numOnly);
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠️ Hafta bilgisi okunamadı, varsayılan kullanılıyor: " + currentWeek);
            }

            System.out.println("🎯 Analiz Edilen Hafta: " + currentWeek + "\n");

            try {
                WebElement table = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#dvFixtureInner table.list-table")));
                List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));

                System.out.println("🏟️  HAFTA " + currentWeek + " FİKSTÜRÜ VE TAHMİNLER:\n");

                int matchCount = 0;
                int predictionCount = 0;

                for (WebElement row : rows) {
                    try {
                        if (row.getAttribute("class") != null && row.getAttribute("class").contains("table-header")) {
                            continue;
                        }

                        List<WebElement> cells = row.findElements(By.tagName("td"));
                        if (cells.size() < 8) continue;

                        String date = cells.get(0).getText().trim();
                        String homeTeam = cells.get(3).getText().trim();
                        String awayTeam = cells.get(7).getText().trim();

                        if (homeTeam.isEmpty() || awayTeam.isEmpty()) continue;

                        String score = "";
                        if (cells.size() > 5) {
                            score = cells.get(5).getText().trim();
                        }

                        matchCount++;
                        System.out.println("━".repeat(70));
                        System.out.println("🆚 MAÇ " + matchCount + ": " + homeTeam + " vs " + awayTeam);
                        System.out.println("📅 Tarih: " + date);

                        if (!score.isEmpty() && score.contains("-") && !score.equals("-") && !score.equals("v")) {
                            System.out.println("⚽ Skor: " + score + " (Maç oynandı)");
                        }

                        boolean found = analyzePatternsForMatch(homeTeam, awayTeam, currentWeek, lastSeason, allMatches, patterns);
                        if (found) predictionCount++;

                    } catch (Exception e) {
                    }
                }

                System.out.println("\n" + "━".repeat(70));
                System.out.println("📊 ÖZET:");
                System.out.println("   Toplam Maç: " + matchCount);
                System.out.println("   Pattern Bulunan Maç: " + predictionCount);
                System.out.println("   Pattern Bulunamayan: " + (matchCount - predictionCount));

                if (matchCount == 0) {
                    System.out.println("\n⚠️  Bu hafta için maç bulunamadı!");
                }

            } catch (Exception e) {
                System.out.println("❌ Maç okuma hatası: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.out.println("❌ Genel tahmin hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean analyzePatternsForMatch(String homeTeam, String awayTeam, int week, String season,
                                            List<Match> allMatches, List<DiscoveredPattern> patterns) {

        List<String> allPredictions = new ArrayList<>();

        for (String team : Arrays.asList(homeTeam, awayTeam)) {

            DiscoveredPattern bestWeekPattern = null;
            double bestAccuracy = 0;

            for (DiscoveredPattern p : patterns) {
                if (p.type.equals("HAFTA_SKOR_TEKRAR") &&
                        p.triggerValue.contains("W" + week + ":") &&
                        p.accuracy > bestAccuracy) {

                    boolean isForThisTeam = false;
                    for (String example : p.examples) {
                        if (example.contains(team)) {
                            isForThisTeam = true;
                            break;
                        }
                    }

                    if (isForThisTeam) {
                        bestWeekPattern = p;
                        bestAccuracy = p.accuracy;
                    }
                }
            }

            if (bestWeekPattern != null) {
                String prediction = String.format("   🎯 %s - HAFTA PATTERN: %s (%.0f%% doğru)",
                        team, bestWeekPattern.triggerValue, bestWeekPattern.accuracy);

                boolean hasPrediction = false;
                if (!bestWeekPattern.totalAfterGap.isEmpty()) {
                    int total = bestWeekPattern.totalAfterGap.getOrDefault(1, 0);
                    int reverse = bestWeekPattern.reverseAfterGap.getOrDefault(1, 0);
                    int drawInv = bestWeekPattern.drawInvolvedAfterGap.getOrDefault(1, 0);

                    if (total > 0) {
                        double reversePerc = (double) reverse / total * 100;
                        double drawPerc = (double) drawInv / total * 100;

                        if (reversePerc >= 50) {
                            prediction += String.format(" → 🔥 1/2-2/1 %.0f%%", reversePerc);
                            hasPrediction = true;
                        } else if (drawPerc >= 50) {
                            prediction += String.format(" → 🔥 1/X-2/X %.0f%%", drawPerc);
                            hasPrediction = true;
                        } else if (reversePerc > 0 || drawPerc > 0) {
                            if (reversePerc > drawPerc && reversePerc >= 30) {
                                prediction += String.format(" → ✅ 1/2-2/1 %.0f%%", reversePerc);
                                hasPrediction = true;
                            } else if (drawPerc >= 30) {
                                prediction += String.format(" → ✅ 1/X-2/X %.0f%%", drawPerc);
                                hasPrediction = true;
                            }
                        }
                    }
                }

                if (!hasPrediction) {
                    prediction += " → ⚠️ Tahmin verisi yetersiz";
                }

                allPredictions.add(prediction);
            }

            List<Match> teamMatches = allMatches.stream()
                    .filter(m -> (m.homeTeam.equals(team) || m.awayTeam.equals(team)) && m.season.equals(season))
                    .filter(m -> m.week < week)
                    .sorted(Comparator.comparingInt(m -> m.week))
                    .collect(Collectors.toList());

            DiscoveredPattern bestSeriesPattern = null;
            int bestSeriesCount = 0;

            if (teamMatches.size() >= 3) {
                for (int len = 3; len <= 4; len++) {
                    if (teamMatches.size() >= len) {
                        List<Match> lastMatches = teamMatches.subList(teamMatches.size() - len, teamMatches.size());
                        List<String> scores = lastMatches.stream()
                                .map(m -> Match.getNormalizedScore(m.ftScore))
                                .filter(s -> s != null)
                                .collect(Collectors.toList());

                        if (scores.size() == len) {
                            String seriesKey = String.join(" + ", scores);

                            for (DiscoveredPattern p : patterns) {
                                if (p.type.equals("SERI_SKOR") &&
                                        p.triggerValue.equals(seriesKey) &&
                                        p.foundCount > bestSeriesCount) {
                                    bestSeriesPattern = p;
                                    bestSeriesCount = p.foundCount;
                                }
                            }
                        }
                    }
                }
            }

            if (bestSeriesPattern != null) {
                String prediction = String.format("   🔥 %s - AKTİF SERİ: %s (%d kez görüldü)",
                        team, bestSeriesPattern.triggerValue, bestSeriesPattern.foundCount);

                if (!bestSeriesPattern.posteriorMatch.isEmpty()) {
                    prediction += " → " + bestSeriesPattern.posteriorMatch;
                }

                if (!bestSeriesPattern.totalAfterGap.isEmpty()) {
                    int total = bestSeriesPattern.totalAfterGap.getOrDefault(1, 0);
                    int reverse = bestSeriesPattern.reverseAfterGap.getOrDefault(1, 0);
                    int drawInv = bestSeriesPattern.drawInvolvedAfterGap.getOrDefault(1, 0);

                    if (total > 0) {
                        double reversePerc = (double) reverse / total * 100;
                        double drawPerc = (double) drawInv / total * 100;

                        if (reversePerc >= 50 || drawPerc >= 50) {
                            String emoji = reversePerc > drawPerc ? "🔥 1/2-2/1" : "🔥 1/X-2/X";
                            double perc = Math.max(reversePerc, drawPerc);
                            prediction += String.format(" → %s %.0f%%", emoji, perc);
                        }
                    }
                }

                allPredictions.add(prediction);
            }

            // DÖRTLÜ CYCLE pattern kontrolü
            if (teamMatches.size() >= 3) {
                List<Match> last3 = teamMatches.subList(Math.max(0, teamMatches.size() - 3), teamMatches.size());
                List<String> last3Norm = last3.stream()
                        .map(m -> getSymmetricalScore(m, team))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (last3Norm.size() == 3) {
                    // ÖNCELİKLE: Son 4 maça bak, pattern zaten tamamlanmış mı?
                    List<String> last4Norm = null;
                    List<String> last5Norm = null;

                    if (teamMatches.size() >= 4) {
                        List<Match> last4 = teamMatches.subList(teamMatches.size() - 4, teamMatches.size());
                        last4Norm = last4.stream()
                                .map(m -> getSymmetricalScore(m, team))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                    }

                    if (teamMatches.size() >= 5) {
                        List<Match> last5 = teamMatches.subList(teamMatches.size() - 5, teamMatches.size());
                        last5Norm = last5.stream()
                                .map(m -> getSymmetricalScore(m, team))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                    }

                    for (DiscoveredPattern p : patterns) {
                        if (!p.type.equals("DORTLU_CYCLE")) continue;

                        if (p.bagKey == null || p.bagKey.isEmpty()) continue;

                        String[] bagElems = p.bagKey.split("\\|");
                        Map<String, Integer> bagCounts = new HashMap<>();
                        for (String be : bagElems) bagCounts.put(be, bagCounts.getOrDefault(be, 0) + 1);

                        // KONTROL 1: Son 4 maç pattern'i zaten tamamlamış mı?
                        if (last4Norm != null && last4Norm.size() == 4) {
                            Map<String, Integer> last4Counts = new HashMap<>();
                            for (String s : last4Norm) last4Counts.put(s, last4Counts.getOrDefault(s, 0) + 1);

                            // Eğer son 4 maç tam olarak bu pattern'i oluşturuyorsa, tahmin yapma!
                            boolean exactMatch = bagCounts.equals(last4Counts);

                            if (exactMatch) {
                                // Pattern zaten tamamlandı, bu pattern için tahmin yapma!
                                System.out.println("[DEBUG] " + team + " - Pattern zaten tamamlanmış (son 4 maç): " + String.join(", ", last4Norm));
                                continue;
                            }
                        }

                        // KONTROL 2: Son 5 maçta bu pattern tamamlanmış mı? (yeni pattern başlamış olabilir)
                        if (last5Norm != null && last5Norm.size() == 5) {
                            // Son 5 maçın ilk 4'ü pattern ise, 5. maç yeni pattern başlangıcı olabilir
                            List<String> first4of5 = last5Norm.subList(0, 4);
                            Map<String, Integer> first4Counts = new HashMap<>();
                            for (String s : first4of5) first4Counts.put(s, first4Counts.getOrDefault(s, 0) + 1);

                            if (bagCounts.equals(first4Counts)) {
                                // İlk 4 maç pattern'di, şimdi 5. maçtayız, yeni cycle başlamış
                                System.out.println("[DEBUG] " + team + " - Pattern 1 maç önce tamamlanmış, yeni cycle başlamış olabilir");
                                continue;
                            }
                        }

                        // KONTROL 3: Son 3 maç, pattern'in 3 elemanını içeriyor mu?
                        Map<String, Integer> last3Counts = new HashMap<>();
                        for (String s : last3Norm) last3Counts.put(s, last3Counts.getOrDefault(s, 0) + 1);

                        boolean allPresent = true;
                        for (var entry : last3Counts.entrySet()) {
                            String key = entry.getKey();
                            int need = entry.getValue();
                            if (bagCounts.getOrDefault(key, 0) < need) {
                                allPresent = false;
                                break;
                            }
                        }

                        if (!allPresent) continue;

                        Map<String, Integer> diff = new HashMap<>(bagCounts);
                        for (var entry : last3Counts.entrySet()) {
                            diff.put(entry.getKey(), diff.get(entry.getKey()) - entry.getValue());
                        }

                        List<String> missing = new ArrayList<>();
                        for (var entry : diff.entrySet()) {
                            for (int c = 0; c < entry.getValue(); c++) missing.add(entry.getKey());
                        }

                        if (missing.size() == 1) {
                            String predictedScore = missing.get(0);
                            String prediction = String.format("   🎯 %s - DÖRTLÜ_CYCLE TAHMİN: Son 3 maç [%s] → 4. maç olası skor: %s  (Pattern: %s, %d kez görüldü, sezonlar: %s)",
                                    team, String.join(", ", last3Norm), predictedScore, p.bagKey, p.foundCount, String.join(", ", p.seasons));
                            allPredictions.add(prediction);
                        }
                    }
                }
            }
        }

        if (!allPredictions.isEmpty()) {
            System.out.println("\n   💡 PATTERN TAHMİNLERİ:");
            for (String pred : allPredictions) {
                System.out.println(pred);
            }
            return true;
        } else {
            System.out.println("\n   ℹ️  Pattern bulunamadı");
            return false;
        }
    }

    public static void main(String[] args) {
        MackolikPatternDiscoveryV5 analyzer = new MackolikPatternDiscoveryV5();
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("🔥🔥🔥 ÇOK SEZONLU PATTERN KEŞİF MAKİNESİ V5 - DÜZELTİLMİŞ 🔥🔥🔥\n");
            System.out.println("🎯 YENİ ÖZELLİKLER:");
            System.out.println("   ✅ HT/FT sadece REVERSE (1/2, 2/1) ve DRAW_INVOLVED (1/X, 2/X)");
            System.out.println("   ✅ Pattern sonrası 1, 2, 3, 4, 5 maç analizi");
            System.out.println("   ✅ DÖRTLÜ CYCLE: Ters skorlar aynı (0-1=1-0), sıra farketmez");
            System.out.println("   ✅ Non-overlapping pencereler (gerçek tekrarlar)\n");

            analyzer.showAvailableLeagues();
            System.out.print("\n🎯 Lig URL'sini yapıştırın (varsayılan Süper Lig): ");
            String leagueUrl = scanner.nextLine().trim();

            if (leagueUrl.isEmpty()) {
                leagueUrl = "https://arsiv.mackolik.com/Puan-Durumu/1/TURKIYE-Super-Lig";
                System.out.println("✅ Varsayılan lig seçildi: Süper Lig");
            }

            Map<String, String> seasonMap = analyzer.getAvailableSeasonsMap(leagueUrl);
            List<String> seasonNames = new ArrayList<>(seasonMap.keySet());

            if (seasonMap.isEmpty()) {
                System.out.println("❌ Sezon bilgisi alınamadı!");
                return;
            }

            System.out.print("\n🔢 Kaç sezon analiz edilsin? (Maks " + seasonNames.size() + "): ");
            int seasonCount = scanner.nextInt();
            scanner.nextLine();

            if (seasonCount <= 0 || seasonCount > seasonNames.size()) {
                seasonCount = Math.min(3, seasonNames.size());
                System.out.println("⚠️  Geçersiz sayı! " + seasonCount + " sezon seçildi.");
            }

            List<String> selectedSeasonIds = new ArrayList<>();
            for (int i = 0; i < seasonCount; i++) {
                String seasonName = seasonNames.get(i);
                String seasonId = seasonMap.get(seasonName);
                selectedSeasonIds.add(seasonId);
            }

            System.out.println("\n🎯 SEÇİLEN SEZONLAR:");
            for (int i = 0; i < seasonCount; i++) {
                String seasonName = seasonNames.get(i);
                String seasonId = selectedSeasonIds.get(i);
                System.out.println((i+1) + ". " + seasonName + " (ID: " + seasonId + ")");
            }

            System.out.print("\n⏳ Başlatmak için ENTER...");
            scanner.nextLine();

            List<Match> matches = analyzer.scrapeMultipleSeasons(selectedSeasonIds, 38);

            if (matches.isEmpty()) {
                System.out.println("❌ Veri yok!");
                return;
            }

            System.out.println("\n🎯 ÇOK SEZONLU PATTERN ANALİZİ BAŞLIYOR...\n");

            List<DiscoveredPattern> allPatterns = new ArrayList<>();

            allPatterns.addAll(analyzer.discoverRecurringScorePatterns(matches));
            allPatterns.addAll(analyzer.discoverReverseScorePatterns(matches));
            allPatterns.addAll(analyzer.discoverSerialScorePatterns(matches));
            allPatterns.addAll(analyzer.discoverQuartetCyclePatterns(matches));
            allPatterns.addAll(analyzer.discoverWeekBasedPatterns(matches));

            allPatterns.sort((a, b) -> Double.compare(b.accuracy, a.accuracy));

            System.out.println("\n🏆🏆🏆 KEŞFEDİLEN ÇOK SEZONLU PATTERN'LER 🏆🏆🏆\n");
            System.out.println("📊 Toplam Maç: " + matches.size());
            System.out.println("💎 Toplam Pattern: " + allPatterns.size());

            allPatterns.forEach(System.out::println);

            if (!allPatterns.isEmpty()) {
                showPatternStatistics(allPatterns);
            }

            System.out.println("\n" + "🎯".repeat(30));

            analyzer.predictUpcomingWeek(matches, allPatterns, leagueUrl);


        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            analyzer.close();
            scanner.close();
        }
    }
}