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

public class MackolikAutoPatternDiscovery {

    private WebDriver driver;
    private WebDriverWait wait;

    static class Match {
        String date;
        String time;
        String dayOfWeek;
        String month;
        String homeTeam;
        String awayTeam;
        String htScore;
        String ftScore;
        int week;
        String season;
        boolean hasKG;

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

        boolean isReverseResult() {
            String result = getHTFTResult();
            return result.equals("1/2") || result.equals("2/1");
        }

        boolean isDrawInvolved() {
            String result = getHTFTResult();
            return result.equals("1/X") || result.equals("2/X") || result.equals("X/1") || result.equals("X/2");
        }

        boolean isOver25Goals() {
            try {
                int total = getFTHome() + getFTAway();
                return total >= 3;
            } catch (Exception e) {
                return false;
            }
        }

        boolean isOver35Goals() {
            try {
                int total = getFTHome() + getFTAway();
                return total >= 4;
            } catch (Exception e) {
                return false;
            }
        }

        boolean isUnder25Goals() {
            try {
                int total = getFTHome() + getFTAway();
                return total < 3;
            } catch (Exception e) {
                return false;
            }
        }

        boolean hasKarsilikliGol() {
            return hasKG || (getFTHome() > 0 && getFTAway() > 0);
        }

        boolean isHomeWin() {
            return getFTHome() > getFTAway();
        }

        boolean isAwayWin() {
            return getFTAway() > getFTHome();
        }

        boolean isDraw() {
            return getFTHome() == getFTAway() && getFTHome() >= 0;
        }

        String getTimeHour() {
            if (time == null || time.isEmpty()) return "Unknown";
            try {
                String[] parts = time.split(":");
                return parts[0] + ":00";
            } catch (Exception e) {
                return "Unknown";
            }
        }

        String getTimeSlot() {
            if (time == null || time.isEmpty()) return "Unknown";
            try {
                int hour = Integer.parseInt(time.split(":")[0]);
                if (hour >= 12 && hour < 15) return "Öğle (12-15)";
                if (hour >= 15 && hour < 18) return "İkindi (15-18)";
                if (hour >= 18 && hour < 21) return "Akşam (18-21)";
                if (hour >= 21 || hour < 3) return "Gece (21-03)";
                return "Sabah";
            } catch (Exception e) {
                return "Unknown";
            }
        }

        @Override
        public String toString() {
            return String.format("%s | %s %s | Hafta %d | %s vs %s | HT:%s FT:%s | 🎯%s",
                    season, dayOfWeek, time, week, homeTeam, awayTeam, htScore, ftScore, getHTFTResult());
        }
    }

    static class AutoDiscoveredPattern {
        String patternType;
        String triggerCondition;
        String resultType;
        int foundCount;
        int totalChecked;
        double accuracy;
        List<String> examples;
        Set<String> seasons;
        Map<String, Object> metadata;

        AutoDiscoveredPattern() {
            this.examples = new ArrayList<>();
            this.seasons = new HashSet<>();
            this.metadata = new HashMap<>();
        }

        void calculate() {
            this.accuracy = totalChecked > 0 ? (double) foundCount / totalChecked * 100 : 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n").append("🔥".repeat(60)).append("\n");
            sb.append("💎 PATTERN KEŞFEDİLDİ: ").append(patternType).append("\n");
            sb.append("🎯 Koşul: ").append(triggerCondition).append("\n");
            sb.append("✨ Sonuç: ").append(resultType).append("\n");
            sb.append("📊 Başarı Oranı: ").append(foundCount).append("/").append(totalChecked)
                    .append(" = ").append(String.format("%.1f%%", accuracy)).append("\n");

            if (!seasons.isEmpty()) {
                sb.append("🏆 Sezonlar (").append(seasons.size()).append("): ")
                        .append(String.join(", ", seasons)).append("\n");
            }

            if (!metadata.isEmpty()) {
                sb.append("ℹ️  Detaylar: ");
                metadata.forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
                sb.append("\n");
            }

            if (!examples.isEmpty()) {
                sb.append("\n📋 ÖRNEKLER (İlk ").append(Math.min(8, examples.size())).append(" örnek):\n");
                for (int i = 0; i < Math.min(8, examples.size()); i++) {
                    sb.append("   ").append(i+1).append(". ").append(examples.get(i)).append("\n");
                }
            }

            sb.append("🔥".repeat(60)).append("\n");
            return sb.toString();
        }
    }

    public MackolikAutoPatternDiscovery() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        driver = new ChromeDriver(options);
        ((JavascriptExecutor) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
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

                try {
                    WebElement fiksturLink = wait.until(ExpectedConditions.presenceOfElementLocated(By.linkText("Fikstür")));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", fiksturLink);
                    Thread.sleep(5000);
                } catch (Exception e) {
                    String fiksturUrl = seasonUrl.replace("Standings", "Fixture");
                    driver.get(fiksturUrl);
                    Thread.sleep(5000);
                }

                try {
                    WebElement firstButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("span.first")));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", firstButton);
                    Thread.sleep(4000);
                } catch (Exception e) {
                    try {
                        WebElement weekSelectElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cboWeek")));
                        Select weekSelect = new Select(weekSelectElement);
                        weekSelect.selectByIndex(0);
                        Thread.sleep(4000);
                    } catch (Exception e2) {
                    }
                }

                int weekCounter = 0;
                int seasonMatchCount = 0;
                int emptyWeekCount = 0;

                while (weekCounter < maxWeeksPerSeason && emptyWeekCount < 2) {
                    weekCounter++;

                    try {
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
                                match.time = cells.get(1).getText().trim();
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
                                    match.hasKG = match.hasKarsilikliGol();
                                    parseDateInfo(match);

                                    allMatches.add(match);
                                    matchesInWeek++;
                                    seasonMatchCount++;
                                }

                            } catch (Exception e) {
                            }
                        }

                        System.out.println("   ✅ Hafta " + weekCounter + ": " + matchesInWeek + " maç (Toplam: " + allMatches.size() + ")");

                        if (matchesInWeek == 0) {
                            emptyWeekCount++;
                            if (emptyWeekCount >= 2) break;
                        } else {
                            emptyWeekCount = 0;
                        }

                        try {
                            WebElement nextButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("span.next")));
                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButton);
                            Thread.sleep(4000);
                        } catch (Exception e) {
                            break;
                        }

                    } catch (Exception e) {
                        break;
                    }
                }

                System.out.println("\n✅ SEZON TAMAMLANDI: " + seasonMatchCount + " maç\n");

            } catch (Exception e) {
                System.out.println("❌ Sezon hatası: " + e.getMessage());
            }
        }

        System.out.println("\n🏆 TÜM SEZONLAR TAMAMLANDI! TOPLAM " + allMatches.size() + " MAÇ!\n");
        return allMatches;
    }

    private void parseDateInfo(Match match) {
        try {
            String[] dateParts = match.date.split("/");
            if (dateParts.length >= 2) {
                int day = Integer.parseInt(dateParts[0]);
                int month = Integer.parseInt(dateParts[1]);

                LocalDate date = LocalDate.of(2024, month, day);
                match.dayOfWeek = getDayName(date.getDayOfWeek().getValue());
                match.month = getMonthName(month);
            }
        } catch (Exception e) {
            match.dayOfWeek = "Unknown";
            match.month = "Unknown";
        }
    }

    private String getDayName(int dayValue) {
        String[] days = {"Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"};
        return dayValue >= 1 && dayValue <= 7 ? days[dayValue - 1] : "Unknown";
    }

    private String getMonthName(int month) {
        String[] months = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
        return month >= 1 && month <= 12 ? months[month - 1] : "Unknown";
    }

    // PATTERN 1: DETAYLI GÜN BAZLI PATTERN'LER
    public List<AutoDiscoveredPattern> autoDiscoverDayPatterns(List<Match> matches) {
        System.out.println("🔍 DETAYLI GÜN BAZLI PATTERN KEŞFİ...\n");
        List<AutoDiscoveredPattern> patterns = new ArrayList<>();

        String[] days = {"Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"};
        String[] results = {"REVERSE", "DRAW_INVOLVED", "OVER_25", "OVER_35", "UNDER_25", "KG", "HOME_WIN", "AWAY_WIN"};

        for (String day : days) {
            for (String resultType : results) {
                AutoDiscoveredPattern pattern = new AutoDiscoveredPattern();
                pattern.patternType = "GÜN_BAZLI";
                pattern.triggerCondition = day + " günü maçlar";

                for (Match m : matches) {
                    if (m.dayOfWeek.equals(day)) {
                        pattern.totalChecked++;

                        boolean matches_result = switch (resultType) {
                            case "REVERSE" -> m.isReverseResult();
                            case "DRAW_INVOLVED" -> m.isDrawInvolved();
                            case "OVER_25" -> m.isOver25Goals();
                            case "OVER_35" -> m.isOver35Goals();
                            case "UNDER_25" -> m.isUnder25Goals();
                            case "KG" -> m.hasKarsilikliGol();
                            case "HOME_WIN" -> m.isHomeWin();
                            case "AWAY_WIN" -> m.isAwayWin();
                            default -> false;
                        };

                        if (matches_result) {
                            pattern.foundCount++;
                            if (pattern.examples.size() < 15) {
                                pattern.examples.add(m.toString());
                            }
                            pattern.seasons.add(m.season);
                        }
                    }
                }

                pattern.resultType = resultType;
                pattern.calculate();

                if (pattern.totalChecked >= 8 && pattern.accuracy >= 35 && pattern.seasons.size() >= 2) {
                    pattern.metadata.put("min_maç", pattern.totalChecked);
                    patterns.add(pattern);
                }
            }
        }

        System.out.println("✅ " + patterns.size() + " gün bazlı pattern keşfedildi!\n");
        return patterns;
    }

    // PATTERN 2: GELİŞMİŞ SAAT DİLİMİ PATTERN'LERİ
    public List<AutoDiscoveredPattern> autoDiscoverTimeSlotPatterns(List<Match> matches) {
        System.out.println("🔍 GELİŞMİŞ SAAT DİLİMİ PATTERN KEŞFİ...\n");
        List<AutoDiscoveredPattern> patterns = new ArrayList<>();

        Map<String, List<Match>> timeGroups = matches.stream()
                .filter(m -> !m.getTimeSlot().equals("Unknown"))
                .collect(Collectors.groupingBy(Match::getTimeSlot));

        String[] results = {"REVERSE", "DRAW_INVOLVED", "OVER_25", "OVER_35", "UNDER_25", "KG", "HOME_WIN", "AWAY_WIN"};

        for (String timeSlot : timeGroups.keySet()) {
            if (timeGroups.get(timeSlot).size() < 8) continue;

            for (String resultType : results) {
                AutoDiscoveredPattern pattern = new AutoDiscoveredPattern();
                pattern.patternType = "SAAT_DİLİMİ";
                pattern.triggerCondition = timeSlot + " maçları";

                for (Match m : timeGroups.get(timeSlot)) {
                    pattern.totalChecked++;

                    boolean matches_result = switch (resultType) {
                        case "REVERSE" -> m.isReverseResult();
                        case "DRAW_INVOLVED" -> m.isDrawInvolved();
                        case "OVER_25" -> m.isOver25Goals();
                        case "OVER_35" -> m.isOver35Goals();
                        case "UNDER_25" -> m.isUnder25Goals();
                        case "KG" -> m.hasKarsilikliGol();
                        case "HOME_WIN" -> m.isHomeWin();
                        case "AWAY_WIN" -> m.isAwayWin();
                        default -> false;
                    };

                    if (matches_result) {
                        pattern.foundCount++;
                        if (pattern.examples.size() < 15) {
                            pattern.examples.add(m.toString());
                        }
                        pattern.seasons.add(m.season);
                    }
                }

                pattern.resultType = resultType;
                pattern.calculate();

                if (pattern.accuracy >= 35 && pattern.seasons.size() >= 2) {
                    pattern.metadata.put("toplam_maç", pattern.totalChecked);
                    patterns.add(pattern);
                }
            }
        }

        System.out.println("✅ " + patterns.size() + " saat dilimi pattern keşfedildi!\n");
        return patterns;
    }

    // PATTERN 3: DETAYLI AY BAZLI PATTERN'LER
    public List<AutoDiscoveredPattern> autoDiscoverMonthPatterns(List<Match> matches) {
        System.out.println("🔍 DETAYLI AY BAZLI PATTERN KEŞFİ...\n");
        List<AutoDiscoveredPattern> patterns = new ArrayList<>();

        String[] months = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
        String[] results = {"REVERSE", "DRAW_INVOLVED", "OVER_25", "OVER_35", "UNDER_25", "KG", "HOME_WIN", "AWAY_WIN"};

        for (String month : months) {
            for (String resultType : results) {
                AutoDiscoveredPattern pattern = new AutoDiscoveredPattern();
                pattern.patternType = "AY_BAZLI";
                pattern.triggerCondition = month + " ayı maçları";

                for (Match m : matches) {
                    if (m.month.equals(month)) {
                        pattern.totalChecked++;

                        boolean matches_result = switch (resultType) {
                            case "REVERSE" -> m.isReverseResult();
                            case "DRAW_INVOLVED" -> m.isDrawInvolved();
                            case "OVER_25" -> m.isOver25Goals();
                            case "OVER_35" -> m.isOver35Goals();
                            case "UNDER_25" -> m.isUnder25Goals();
                            case "KG" -> m.hasKarsilikliGol();
                            case "HOME_WIN" -> m.isHomeWin();
                            case "AWAY_WIN" -> m.isAwayWin();
                            default -> false;
                        };

                        if (matches_result) {
                            pattern.foundCount++;
                            if (pattern.examples.size() < 15) {
                                pattern.examples.add(m.toString());
                            }
                            pattern.seasons.add(m.season);
                        }
                    }
                }

                pattern.resultType = resultType;
                pattern.calculate();

                if (pattern.totalChecked >= 15 && pattern.accuracy >= 35 && pattern.seasons.size() >= 2) {
                    pattern.metadata.put("ay_maç_sayısı", pattern.totalChecked);
                    patterns.add(pattern);
                }
            }
        }

        System.out.println("✅ " + patterns.size() + " ay bazlı pattern keşfedildi!\n");
        return patterns;
    }

    // PATTERN 4: SÜPER GÜÇLENDİRİLMİŞ GÜN+SAAT KOMBİNASYONU
    public List<AutoDiscoveredPattern> autoDiscoverDayTimeCombos(List<Match> matches) {
        System.out.println("🔍 SÜPER GÜÇLENDİRİLMİŞ GÜN+SAAT PATTERN KEŞFİ...\n");
        List<AutoDiscoveredPattern> patterns = new ArrayList<>();

        Map<String, List<Match>> combos = matches.stream()
                .filter(m -> !m.dayOfWeek.equals("Unknown") && !m.getTimeSlot().equals("Unknown"))
                .collect(Collectors.groupingBy(m -> m.dayOfWeek + " + " + m.getTimeSlot()));

        String[] results = {"REVERSE", "DRAW_INVOLVED", "OVER_25", "OVER_35", "UNDER_25", "KG", "HOME_WIN", "AWAY_WIN"};

        for (String combo : combos.keySet()) {
            List<Match> comboMatches = combos.get(combo);
            if (comboMatches.size() < 5) continue;

            for (String resultType : results) {
                AutoDiscoveredPattern pattern = new AutoDiscoveredPattern();
                pattern.patternType = "GÜN_SAAT_SÜPER_COMBO";
                pattern.triggerCondition = combo;

                for (Match m : comboMatches) {
                    pattern.totalChecked++;

                    boolean matches_result = switch (resultType) {
                        case "REVERSE" -> m.isReverseResult();
                        case "DRAW_INVOLVED" -> m.isDrawInvolved();
                        case "OVER_25" -> m.isOver25Goals();
                        case "OVER_35" -> m.isOver35Goals();
                        case "UNDER_25" -> m.isUnder25Goals();
                        case "KG" -> m.hasKarsilikliGol();
                        case "HOME_WIN" -> m.isHomeWin();
                        case "AWAY_WIN" -> m.isAwayWin();
                        default -> false;
                    };

                    if (matches_result) {
                        pattern.foundCount++;
                        if (pattern.examples.size() < 15) {
                            pattern.examples.add(m.toString());
                        }
                        pattern.seasons.add(m.season);
                    }
                }

                pattern.resultType = resultType;
                pattern.calculate();

                if (pattern.accuracy >= 45 && pattern.seasons.size() >= 2) {
                    pattern.metadata.put("combo_maç", pattern.totalChecked);
                    pattern.metadata.put("güç", "YÜKSEK");
                    patterns.add(pattern);
                }
            }
        }

        System.out.println("✅ " + patterns.size() + " süper gün+saat combo pattern keşfedildi!\n");
        return patterns;
    }

    // PATTERN 5: GELİŞMİŞ SON MAÇ SKORU PATTERN'LERİ
    public List<AutoDiscoveredPattern> autoDiscoverLastMatchScorePatterns(List<Match> matches) {
        System.out.println("🔍 GELİŞMİŞ SON MAÇ SKORU PATTERN KEŞFİ...\n");
        List<AutoDiscoveredPattern> patterns = new ArrayList<>();

        Map<String, List<Match>> teamMatches = new HashMap<>();
        for (Match m : matches) {
            teamMatches.computeIfAbsent(m.homeTeam, k -> new ArrayList<>()).add(m);
            teamMatches.computeIfAbsent(m.awayTeam, k -> new ArrayList<>()).add(m);
        }

        for (String team : teamMatches.keySet()) {
            List<Match> games = teamMatches.get(team);
            games.sort(Comparator.comparing(m -> m.season + "-" + String.format("%02d", m.week)));
        }

        Set<String> commonScores = new HashSet<>();
        Map<String, Integer> scoreFreq = new HashMap<>();
        for (Match m : matches) {
            if (m.ftScore != null) {
                scoreFreq.put(m.ftScore, scoreFreq.getOrDefault(m.ftScore, 0) + 1);
            }
        }
        scoreFreq.forEach((score, freq) -> {
            if (freq >= 10) commonScores.add(score);
        });

        String[] results = {"REVERSE", "DRAW_INVOLVED", "OVER_25", "OVER_35", "UNDER_25", "KG", "HOME_WIN", "AWAY_WIN"};

        for (String triggerScore : commonScores) {
            for (String resultType : results) {
                AutoDiscoveredPattern pattern = new AutoDiscoveredPattern();
                pattern.patternType = "SON_MAÇ_SKORU_GELİŞMİŞ";
                pattern.triggerCondition = "Son maç " + triggerScore + " bitti";

                for (String team : teamMatches.keySet()) {
                    List<Match> games = teamMatches.get(team);

                    for (int i = 1; i < games.size(); i++) {
                        Match prevMatch = games.get(i - 1);
                        Match currentMatch = games.get(i);

                        if (prevMatch.ftScore != null && prevMatch.ftScore.equals(triggerScore)) {
                            pattern.totalChecked++;

                            boolean matches_result = switch (resultType) {
                                case "REVERSE" -> currentMatch.isReverseResult();
                                case "DRAW_INVOLVED" -> currentMatch.isDrawInvolved();
                                case "OVER_25" -> currentMatch.isOver25Goals();
                                case "OVER_35" -> currentMatch.isOver35Goals();
                                case "UNDER_25" -> currentMatch.isUnder25Goals();
                                case "KG" -> currentMatch.hasKarsilikliGol();
                                case "HOME_WIN" -> currentMatch.isHomeWin();
                                case "AWAY_WIN" -> currentMatch.isAwayWin();
                                default -> false;
                            };

                            if (matches_result) {
                                pattern.foundCount++;
                                if (pattern.examples.size() < 12) {
                                    pattern.examples.add("Takım: " + team + " | Önceki: " + prevMatch.ftScore +
                                            " ➜ Sonraki: " + currentMatch.toString());
                                }
                                pattern.seasons.add(currentMatch.season);
                            }
                        }
                    }
                }

                pattern.resultType = resultType;
                pattern.calculate();

                if (pattern.totalChecked >= 6 && pattern.accuracy >= 45 && pattern.seasons.size() >= 2) {
                    pattern.metadata.put("tetikleyici_skor", triggerScore);
                    patterns.add(pattern);
                }
            }
        }

        System.out.println("✅ " + patterns.size() + " son maç skoru pattern keşfedildi!\n");
        return patterns;
    }

    // PATTERN 6: SÜPER GÜÇLENDİRİLMİŞ KG SERİSİ PATTERN'LERİ
    public List<AutoDiscoveredPattern> autoDiscoverKGStreakPatterns(List<Match> matches) {
        System.out.println("🔍 SÜPER GÜÇLENDİRİLMİŞ KG SERİSİ PATTERN KEŞFİ...\n");
        List<AutoDiscoveredPattern> patterns = new ArrayList<>();

        Map<String, List<Match>> teamMatches = new HashMap<>();
        for (Match m : matches) {
            teamMatches.computeIfAbsent(m.homeTeam, k -> new ArrayList<>()).add(m);
            teamMatches.computeIfAbsent(m.awayTeam, k -> new ArrayList<>()).add(m);
        }

        for (String team : teamMatches.keySet()) {
            List<Match> games = teamMatches.get(team);
            games.sort(Comparator.comparing(m -> m.season + "-" + String.format("%02d", m.week)));
        }

        int[] streakLengths = {2, 3, 4, 5};
        String[] results = {"REVERSE", "DRAW_INVOLVED", "OVER_25", "OVER_35", "UNDER_25", "KG", "HOME_WIN", "AWAY_WIN"};

        for (int streakLen : streakLengths) {
            for (String resultType : results) {
                AutoDiscoveredPattern pattern = new AutoDiscoveredPattern();
                pattern.patternType = "KG_SERİSİ_SÜPER";
                pattern.triggerCondition = "Son " + streakLen + " maçta KG var";

                for (String team : teamMatches.keySet()) {
                    List<Match> games = teamMatches.get(team);

                    for (int i = streakLen; i < games.size(); i++) {
                        boolean hasStreak = true;
                        for (int j = i - streakLen; j < i; j++) {
                            if (!games.get(j).hasKarsilikliGol()) {
                                hasStreak = false;
                                break;
                            }
                        }

                        if (hasStreak) {
                            Match currentMatch = games.get(i);
                            pattern.totalChecked++;

                            boolean matches_result = switch (resultType) {
                                case "REVERSE" -> currentMatch.isReverseResult();
                                case "DRAW_INVOLVED" -> currentMatch.isDrawInvolved();
                                case "OVER_25" -> currentMatch.isOver25Goals();
                                case "OVER_35" -> currentMatch.isOver35Goals();
                                case "UNDER_25" -> currentMatch.isUnder25Goals();
                                case "KG" -> currentMatch.hasKarsilikliGol();
                                case "HOME_WIN" -> currentMatch.isHomeWin();
                                case "AWAY_WIN" -> currentMatch.isAwayWin();
                                default -> false;
                            };

                            if (matches_result) {
                                pattern.foundCount++;
                                if (pattern.examples.size() < 12) {
                                    pattern.examples.add(team + " | " + currentMatch.toString());
                                }
                                pattern.seasons.add(currentMatch.season);
                            }
                        }
                    }
                }

                pattern.resultType = resultType;
                pattern.calculate();

                if (pattern.totalChecked >= 5 && pattern.accuracy >= 40 && pattern.seasons.size() >= 2) {
                    pattern.metadata.put("seri_uzunluk", streakLen);
                    pattern.metadata.put("güç", streakLen >= 4 ? "ÇOK_YÜKSEK" : "YÜKSEK");
                    patterns.add(pattern);
                }
            }
        }

        System.out.println("✅ " + patterns.size() + " KG serisi pattern keşfedildi!\n");
        return patterns;
    }

    // PATTERN 7: GELİŞMİŞ TEK MAÇ PATTERN'LERİ
    public List<AutoDiscoveredPattern> autoDiscoverSingleMatchPatterns(List<Match> matches) {
        System.out.println("🔍 GELİŞMİŞ TEK MAÇ PATTERN KEŞFİ...\n");
        List<AutoDiscoveredPattern> patterns = new ArrayList<>();

        Map<String, Map<Integer, List<Match>>> seasonWeekMatches = new HashMap<>();
        for (Match m : matches) {
            seasonWeekMatches.computeIfAbsent(m.season, k -> new HashMap<>())
                    .computeIfAbsent(m.week, k -> new ArrayList<>())
                    .add(m);
        }

        String[] days = {"Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"};
        String[] results = {"REVERSE", "DRAW_INVOLVED", "OVER_25", "OVER_35", "UNDER_25", "KG", "HOME_WIN", "AWAY_WIN"};

        for (String day : days) {
            for (String resultType : results) {
                AutoDiscoveredPattern pattern = new AutoDiscoveredPattern();
                pattern.patternType = "TEK_MAÇ_GELİŞMİŞ";
                pattern.triggerCondition = day + " günü haftanın tek maçı";

                for (String season : seasonWeekMatches.keySet()) {
                    for (int week : seasonWeekMatches.get(season).keySet()) {
                        List<Match> weekMatches = seasonWeekMatches.get(season).get(week);
                        List<Match> dayMatches = weekMatches.stream()
                                .filter(m -> m.dayOfWeek.equals(day))
                                .collect(Collectors.toList());

                        if (dayMatches.size() == 1) {
                            Match m = dayMatches.get(0);
                            pattern.totalChecked++;

                            boolean matches_result = switch (resultType) {
                                case "REVERSE" -> m.isReverseResult();
                                case "DRAW_INVOLVED" -> m.isDrawInvolved();
                                case "OVER_25" -> m.isOver25Goals();
                                case "OVER_35" -> m.isOver35Goals();
                                case "UNDER_25" -> m.isUnder25Goals();
                                case "KG" -> m.hasKarsilikliGol();
                                case "HOME_WIN" -> m.isHomeWin();
                                case "AWAY_WIN" -> m.isAwayWin();
                                default -> false;
                            };

                            if (matches_result) {
                                pattern.foundCount++;
                                if (pattern.examples.size() < 12) {
                                    pattern.examples.add(m.toString());
                                }
                                pattern.seasons.add(m.season);
                            }
                        }
                    }
                }

                pattern.resultType = resultType;
                pattern.calculate();

                if (pattern.totalChecked >= 3 && pattern.accuracy >= 45 && pattern.seasons.size() >= 2) {
                    pattern.metadata.put("özellik", "Haftanın_TEK_maçı");
                    pattern.metadata.put("güç", "YÜKSEK");
                    patterns.add(pattern);
                }
            }
        }

        System.out.println("✅ " + patterns.size() + " tek maç pattern keşfedildi!\n");
        return patterns;
    }

    // YENİ PATTERN 8: HAFTA BAZLI PATTERN'LER
    public List<AutoDiscoveredPattern> autoDiscoverWeekBasedPatterns(List<Match> matches) {
        System.out.println("🔍 HAFTA BAZLI PATTERN KEŞFİ...\n");
        List<AutoDiscoveredPattern> patterns = new ArrayList<>();

        String[] results = {"REVERSE", "DRAW_INVOLVED", "OVER_25", "OVER_35", "UNDER_25", "KG"};
        int[] weekRanges = {1, 5, 10, 15, 20, 25, 30, 35};

        for (int weekStart : weekRanges) {
            int weekEnd = weekStart + 4;

            for (String resultType : results) {
                AutoDiscoveredPattern pattern = new AutoDiscoveredPattern();
                pattern.patternType = "HAFTA_ARALIĞI";
                pattern.triggerCondition = "Hafta " + weekStart + "-" + weekEnd + " arası";

                for (Match m : matches) {
                    if (m.week >= weekStart && m.week <= weekEnd) {
                        pattern.totalChecked++;

                        boolean matches_result = switch (resultType) {
                            case "REVERSE" -> m.isReverseResult();
                            case "DRAW_INVOLVED" -> m.isDrawInvolved();
                            case "OVER_25" -> m.isOver25Goals();
                            case "OVER_35" -> m.isOver35Goals();
                            case "UNDER_25" -> m.isUnder25Goals();
                            case "KG" -> m.hasKarsilikliGol();
                            default -> false;
                        };

                        if (matches_result) {
                            pattern.foundCount++;
                            if (pattern.examples.size() < 12) {
                                pattern.examples.add(m.toString());
                            }
                            pattern.seasons.add(m.season);
                        }
                    }
                }

                pattern.resultType = resultType;
                pattern.calculate();

                if (pattern.totalChecked >= 15 && pattern.accuracy >= 35 && pattern.seasons.size() >= 2) {
                    pattern.metadata.put("hafta_aralığı", weekStart + "-" + weekEnd);
                    patterns.add(pattern);
                }
            }
        }

        System.out.println("✅ " + patterns.size() + " hafta bazlı pattern keşfedildi!\n");
        return patterns;
    }

    // YENİ PATTERN 9: HT/FT DETAYLI PATTERN'LER
    public List<AutoDiscoveredPattern> autoDiscoverHTFTDetailedPatterns(List<Match> matches) {
        System.out.println("🔍 DETAYLI HT/FT PATTERN KEŞFİ...\n");
        List<AutoDiscoveredPattern> patterns = new ArrayList<>();

        String[] htftResults = {"1/1", "1/X", "1/2", "X/1", "X/X", "X/2", "2/1", "2/X", "2/2"};
        String[] nextResults = {"REVERSE", "DRAW_INVOLVED", "OVER_25", "KG"};

        Map<String, List<Match>> teamMatches = new HashMap<>();
        for (Match m : matches) {
            teamMatches.computeIfAbsent(m.homeTeam, k -> new ArrayList<>()).add(m);
            teamMatches.computeIfAbsent(m.awayTeam, k -> new ArrayList<>()).add(m);
        }

        for (String team : teamMatches.keySet()) {
            List<Match> games = teamMatches.get(team);
            games.sort(Comparator.comparing(m -> m.season + "-" + String.format("%02d", m.week)));
        }

        for (String htftTrigger : htftResults) {
            for (String nextResult : nextResults) {
                AutoDiscoveredPattern pattern = new AutoDiscoveredPattern();
                pattern.patternType = "HT_FT_DETAYLI";
                pattern.triggerCondition = "Son maç HT/FT: " + htftTrigger;

                for (String team : teamMatches.keySet()) {
                    List<Match> games = teamMatches.get(team);

                    for (int i = 1; i < games.size(); i++) {
                        Match prevMatch = games.get(i - 1);
                        Match currentMatch = games.get(i);

                        if (prevMatch.getHTFTResult().equals(htftTrigger)) {
                            pattern.totalChecked++;

                            boolean matches_result = switch (nextResult) {
                                case "REVERSE" -> currentMatch.isReverseResult();
                                case "DRAW_INVOLVED" -> currentMatch.isDrawInvolved();
                                case "OVER_25" -> currentMatch.isOver25Goals();
                                case "KG" -> currentMatch.hasKarsilikliGol();
                                default -> false;
                            };

                            if (matches_result) {
                                pattern.foundCount++;
                                if (pattern.examples.size() < 12) {
                                    pattern.examples.add(team + " | Önceki: " + prevMatch.getHTFTResult() +
                                            " ➜ " + currentMatch.toString());
                                }
                                pattern.seasons.add(currentMatch.season);
                            }
                        }
                    }
                }

                pattern.resultType = nextResult;
                pattern.calculate();

                if (pattern.totalChecked >= 5 && pattern.accuracy >= 50 && pattern.seasons.size() >= 2) {
                    pattern.metadata.put("tetikleyici_HTFT", htftTrigger);
                    pattern.metadata.put("güç", "ÇOK_YÜKSEK");
                    patterns.add(pattern);
                }
            }
        }

        System.out.println("✅ " + patterns.size() + " HT/FT detaylı pattern keşfedildi!\n");
        return patterns;
    }

    public void close() {
        if (driver != null) driver.quit();
    }

    public static void main(String[] args) {
        MackolikAutoPatternDiscovery analyzer = new MackolikAutoPatternDiscovery();
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("🔥".repeat(30));
            System.out.println("💎 OTOMATİK PATTERN KEŞİF MAKİNESİ v2.0 💎");
            System.out.println("🔥".repeat(30));
            System.out.println("\n🚀 YENİ VE GÜÇLENDİRİLMİŞ PATTERN'LER:");
            System.out.println("   ✅ Detaylı Gün Bazlı (8 farklı sonuç tipi)");
            System.out.println("   ✅ Gelişmiş Saat Dilimleri (Öğle/İkindi/Akşam/Gece)");
            System.out.println("   ✅ Detaylı Ay Bazlı");
            System.out.println("   ✅ Süper Güçlendirilmiş Gün+Saat Combo");
            System.out.println("   ✅ Gelişmiş Son Maç Skoru");
            System.out.println("   ✅ Süper Güçlendirilmiş KG Serisi");
            System.out.println("   ✅ Gelişmiş Tek Maç");
            System.out.println("   ✅ YENİ: Hafta Aralığı Pattern'leri");
            System.out.println("   ✅ YENİ: HT/FT Detaylı Pattern'ler\n");

            System.out.print("🎯 Lig URL'sini yapıştırın (varsayılan Süper Lig): ");
            String leagueUrl = scanner.nextLine().trim();

            if (leagueUrl.isEmpty()) {
                leagueUrl = "https://arsiv.mackolik.com/Puan-Durumu/1/TURKIYE-Super-Lig";
                System.out.println("✅ Varsayılan lig: Süper Lig");
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
                seasonCount = Math.min(6, seasonNames.size());
                System.out.println("⚠️ " + seasonCount + " sezon seçildi.");
            }

            List<String> selectedSeasonIds = new ArrayList<>();
            for (int i = 0; i < seasonCount; i++) {
                selectedSeasonIds.add(seasonMap.get(seasonNames.get(i)));
            }

            System.out.println("\n🎯 SEÇİLEN SEZONLAR:");
            for (int i = 0; i < seasonCount; i++) {
                System.out.println((i+1) + ". " + seasonNames.get(i));
            }

            System.out.print("\n⏳ Başlatmak için ENTER...");
            scanner.nextLine();

            List<Match> matches = analyzer.scrapeMultipleSeasons(selectedSeasonIds, 38);

            if (matches.isEmpty()) {
                System.out.println("❌ Veri yok!");
                return;
            }

            System.out.println("\n🤖 GÜÇLENDİRİLMİŞ PATTERN KEŞFİ BAŞLIYOR...\n");

            List<AutoDiscoveredPattern> allPatterns = new ArrayList<>();

            allPatterns.addAll(analyzer.autoDiscoverDayPatterns(matches));
            allPatterns.addAll(analyzer.autoDiscoverTimeSlotPatterns(matches));
            allPatterns.addAll(analyzer.autoDiscoverMonthPatterns(matches));
            allPatterns.addAll(analyzer.autoDiscoverDayTimeCombos(matches));
            allPatterns.addAll(analyzer.autoDiscoverLastMatchScorePatterns(matches));
            allPatterns.addAll(analyzer.autoDiscoverKGStreakPatterns(matches));
            allPatterns.addAll(analyzer.autoDiscoverSingleMatchPatterns(matches));
            allPatterns.addAll(analyzer.autoDiscoverWeekBasedPatterns(matches));
            allPatterns.addAll(analyzer.autoDiscoverHTFTDetailedPatterns(matches));

            allPatterns.sort((a, b) -> Double.compare(b.accuracy, a.accuracy));

            System.out.println("\n" + "🏆".repeat(30));
            System.out.println("💎 KEŞFEDİLEN PATTERN'LER 💎");
            System.out.println("🏆".repeat(30));
            System.out.println("\n📊 Toplam Maç: " + matches.size());
            System.out.println("💎 Keşfedilen Pattern: " + allPatterns.size() + "\n");

            if (allPatterns.isEmpty()) {
                System.out.println("❌ Pattern bulunamadı! Daha fazla sezon ekleyin.");
            } else {
                int displayCount = Math.min(30, allPatterns.size());
                System.out.println("🔝 EN İYİ " + displayCount + " PATTERN GÖSTERİLİYOR:\n");

                for (int i = 0; i < displayCount; i++) {
                    System.out.println(allPatterns.get(i));
                }

                System.out.println("\n📈 DETAYLI İSTATİSTİKLER:");
                double avgAccuracy = allPatterns.stream().mapToDouble(p -> p.accuracy).average().orElse(0);
                double maxAccuracy = allPatterns.stream().mapToDouble(p -> p.accuracy).max().orElse(0);

                System.out.println("✅ En Yüksek Başarı: %" + String.format("%.1f", maxAccuracy));
                System.out.println("📊 Ortalama Başarı: %" + String.format("%.1f", avgAccuracy));

                Map<String, Long> typeDistribution = allPatterns.stream()
                        .collect(Collectors.groupingBy(p -> p.patternType, Collectors.counting()));

                System.out.println("\n🏷️ PATTERN TİP DAĞILIMI:");
                typeDistribution.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .forEach(e -> System.out.println("   " + e.getKey() + ": " + e.getValue()));

                long ultra = allPatterns.stream().filter(p -> p.accuracy >= 80).count();
                long high = allPatterns.stream().filter(p -> p.accuracy >= 60 && p.accuracy < 80).count();
                long medium = allPatterns.stream().filter(p -> p.accuracy >= 45 && p.accuracy < 60).count();
                long low = allPatterns.stream().filter(p -> p.accuracy >= 35 && p.accuracy < 45).count();

                System.out.println("\n🎖️ BAŞARI DAĞILIMI:");
                System.out.println("   💎 %80+ Ultra: " + ultra);
                System.out.println("   🥇 %60-80 Yüksek: " + high);
                System.out.println("   🥈 %45-60 Orta: " + medium);
                System.out.println("   🥉 %35-45 Düşük: " + low);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            analyzer.close();
            scanner.close();
        }
    }
}