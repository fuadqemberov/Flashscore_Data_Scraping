package analyzer.mackolik.before_after_pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MackolikHalfTimePatternFinder {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final List<String> matchedPatterns = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger completedCount = new AtomicInteger(0);

    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final Semaphore semaphore = new Semaphore(20);

    static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        System.out.println("🚀 Sistem başlatıldı");
        System.out.println("📊 Paralel istek limiti: 20\n");

        System.out.println("📍 Maç ID'leri toplanıyor...\n");
        Set<String> matchIds = fetchMatchIdsFromAPI();

        if (matchIds.isEmpty()) {
            System.out.println("❌ Hiç maç bulunamadı!");
            return;
        }

        System.out.println("================================");
        System.out.println("📋 Toplanan Maç ID Sayısı: " + matchIds.size());
        System.out.println("================================\n");
        System.out.println("⚙️  ANALİZ BAŞLIYOR...\n");

        List<Future<?>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String id : matchIds) {
                Future<?> future = executor.submit(() -> analyzeMatch(id));
                futures.add(future);
            }

            System.out.println("⏳ Tüm maç analizleri tamamlanıyor...");
            int done = 0;
            for (Future<?> future : futures) {
                try {
                    future.get(120, TimeUnit.SECONDS);
                    done++;
                    printProgress(done, matchIds.size());
                } catch (TimeoutException te) {
                    future.cancel(true);
                } catch (Exception ignored) {}
            }
        }

        printResults(startTime);
    }

    // ─────────────────────────────────────────────────────────────
    // API'den Maç ID'lerini Çek
    // ─────────────────────────────────────────────────────────────
    private static Set<String> fetchMatchIdsFromAPI() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            String apiUrl = "https://vd.mackolik.com/livedata?group=0";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Pattern p = Pattern.compile("\\[(\\d{7}),");
            Matcher m = p.matcher(response.body());
            while (m.find()) ids.add(m.group(1));

            System.out.println("Toplam " + ids.size() + " adet maç ID'si bulundu.");
        } catch (Exception e) {
            System.err.println("❌ API Hatası: " + e.getMessage());
        }
        return ids;
    }

    // ─────────────────────────────────────────────────────────────
    // Maç Analizi
    // ─────────────────────────────────────────────────────────────
    private static void analyzeMatch(String matchId) {
        int maxRetry = 3;
        int attempt = 0;

        while (attempt < maxRetry) {
            attempt++;
            try {
                semaphore.acquire();
                try {
                    String url = "https://arsiv.mackolik.com/Match/Head2Head.aspx?id=" + matchId + "&s=1";
                    String matchUrl = "https://arsiv.mackolik.com/Mac/" + matchId + "/";

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("User-Agent", USER_AGENT)
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    Document doc = Jsoup.parse(response.body());

                    Elements forms = doc.select("div.md:has(div.detail-title:contains(Form Durumu))");
                    if (forms.size() < 2) return;

                    TableAnalysis home = parseForm(forms.get(0), matchId);
                    TableAnalysis away = parseForm(forms.get(1), matchId);

                    checkPatterns(home, away, matchUrl);
                    checkTriplePattern(home, away, matchUrl);  // YENİ PATTERN
                    return;

                } finally {
                    semaphore.release();
                }

            } catch (java.net.http.HttpTimeoutException e) {
                if (attempt < maxRetry) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                return;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Form Tablosunu Parse Et (GÜNCELLENDİ)
    // ─────────────────────────────────────────────────────────────
    private static TableAnalysis parseForm(Element container, String targetId) {
        String fullTitle = container.select(".detail-title").text();
        String teamName;
        if (fullTitle.contains("-")) {
            teamName = fullTitle.split("-")[0].trim();
        } else {
            teamName = fullTitle.replace("Form Durumu", "").trim();
        }

        Elements rows = container.select("tr.row, tr.row-2");
        List<PastMatch> pastMatches = new ArrayList<>();
        String upcomingLeague = null;

        // Eski pattern için hala lazım
        int targetIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements scoreLinks = row.select("td:nth-child(4) a");
            if (scoreLinks.isEmpty()) continue;

            String href = scoreLinks.first().attr("href");
            boolean isTarget = href.contains(targetId);
            if (isTarget) targetIndex = i;

            // Lig kısaltması
            String league = row.select("td:first-child a").text().trim();

            // Tarih
            String date = row.select("td:nth-child(2)").text().trim();

            // Ev sahibi ve deplasman
            Element homeElem = row.select("td:nth-child(3) a[href*=/Takim/], td:nth-child(3) span.team").first();
            Element awayElem = row.select("td:nth-child(5) a[href*=/Takim/], td:nth-child(5) span.team").first();
            if (homeElem == null || awayElem == null) continue;
            String homeTeam = homeElem.text().trim();
            String awayTeam = awayElem.text().trim();

            // Skor ve tamamlanma durumu
            String scoreText = scoreLinks.first().text().trim();
            boolean completed = scoreText.matches(".*\\d+.*-.*\\d+.*");

            // Maç ID'sini skor linkinden al
            String matchId = "";
            Matcher idMatcher = Pattern.compile("/Mac/(\\d+)/").matcher(href);
            if (idMatcher.find()) matchId = idMatcher.group(1);

            PastMatch pm = new PastMatch(league, date, matchId, homeTeam, awayTeam, scoreText, completed);
            pastMatches.add(pm);

            // Eğer bu satır analiz edilen maç ise ve henüz oynanmamışsa ligini al
            if (isTarget && !completed) {
                upcomingLeague = league;
            }
        }

        // Eski patternler için çapraz rakamlar (opsiyonel olarak hala kullanılıyor)
        String prev2 = null, prev1 = null, next1 = null, next2 = null;
        if (targetIndex != -1) {
            prev2 = getOpponentFromRow(rows, targetIndex - 2);
            prev1 = getOpponentFromRow(rows, targetIndex - 1);
            next1 = getOpponentFromRow(rows, targetIndex + 1);
            next2 = getOpponentFromRow(rows, targetIndex + 2);
        }

        // Bağımsız skor serisi için son 2 tamamlanmış skor
        List<String> playedScores = new ArrayList<>();
        for (PastMatch pm : pastMatches) {
            if (pm.completed) {
                playedScores.add(pm.score);
            }
        }
        String last2Score = playedScores.size() >= 2 ? playedScores.get(playedScores.size() - 2) : null;
        String last1Score = playedScores.size() >= 2 ? playedScores.get(playedScores.size() - 1) : null;

        return new TableAnalysis(teamName, prev2, prev1, next1, next2, last2Score, last1Score, pastMatches, upcomingLeague);
    }

    private static String getOpponentFromRow(Elements rows, int index) {
        if (index < 0 || index >= rows.size()) return null;
        Elements links = rows.get(index).select(
                "td:nth-child(3) a[href*=/Takim/], td:nth-child(5) a[href*=/Takim/]");
        return links.isEmpty() ? null : links.first().text().trim();
    }

    private static boolean isScoreMatch(String actualScore, String... acceptedScores) {
        if (actualScore == null) return false;
        String cleanScore = actualScore.replace(" ", "");
        for (String accepted : acceptedScores) {
            if (cleanScore.equals(accepted)) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    // Pattern Kontrolleri (ESKİ)
    // ─────────────────────────────────────────────────────────────
    private static void checkPatterns(TableAnalysis h, TableAnalysis a, String url) {
        // Bağımsız skor serisi
        boolean homeSequenceMatch = isScoreMatch(h.last2Score, "1-0", "0-1") && isScoreMatch(h.last1Score, "2-1", "1-2");
        boolean awaySequenceMatch = isScoreMatch(a.last2Score, "1-0", "0-1") && isScoreMatch(a.last1Score, "2-1", "1-2");

        if (homeSequenceMatch) {
            recordMatch(url, h.teamName, a.teamName, Set.of("Maç 1: " + h.last2Score + " | Maç 2: " + h.last1Score),
                    "🎯 BAĞIMSIZ SKOR SERİSİ (EV SAHİBİ)", "Ev Sahibinin oynadığı son 2 maç sırasıyla (1-0 / 0-1) ve ardından (2-1 / 1-2) bitti.", "🎯");
        }
        if (awaySequenceMatch) {
            recordMatch(url, h.teamName, a.teamName, Set.of("Maç 1: " + a.last2Score + " | Maç 2: " + a.last1Score),
                    "🎯 BAĞIMSIZ SKOR SERİSİ (DEPLASMAN)", "Deplasmanın oynadığı son 2 maç sırasıyla (1-0 / 0-1) ve ardından (2-1 / 1-2) bitti.", "🎯");
        }

        // Çapraz havuz eşleşmeleri
        Set<String> matchType1 = new HashSet<>(h.prevOpponents);
        matchType1.retainAll(a.nextOpponents);

        Set<String> matchType2 = new HashSet<>(h.nextOpponents);
        matchType2.retainAll(a.prevOpponents);

        if (matchType1.size() >= 2)
            recordMatch(url, h.teamName, a.teamName, matchType1,
                    "BİLGİ-3 (Havuz Eşleşmesi)", "A'nın Önceki 2 Maçı = B'nin Sonraki 2 Maçı", "🟢");
        if (matchType2.size() >= 2)
            recordMatch(url, h.teamName, a.teamName, matchType2,
                    "BİLGİ-3 (Havuz Eşleşmesi)", "A'nın Sonraki 2 Maçı = B'nin Önceki 2 Maçı", "🟢");

        boolean c1A = h.prev1 != null && h.prev1.equals(a.next1);
        boolean c1B = h.next1 != null && h.next1.equals(a.prev1);
        boolean c2A = h.prev2 != null && h.prev2.equals(a.next2);
        boolean c2B = h.next2 != null && h.next2.equals(a.prev2);

        if (c1A && c1B) {
            recordMatch(url, h.teamName, a.teamName, Set.of(h.prev1, h.next1),
                    "🔥 KUSURSUZ X ÇAPRAZ (Mesafe 1)", "Ev[-1] = Dep[+1] VE Ev[+1] = Dep[-1]", "🔥");
        } else {
            if (c1A) recordMatch(url, h.teamName, a.teamName, Set.of(h.prev1),
                    "YENİ PATTERN (1. Mesafe Çapraz)", "Ev Sahibinin 1 Önceki Rakibi [-1] = Deplasmanın 1 Sonraki Rakibi [+1]", "🔵");
            if (c1B) recordMatch(url, h.teamName, a.teamName, Set.of(h.next1),
                    "YENİ PATTERN (1. Mesafe Çapraz)", "Ev Sahibinin 1 Sonraki Rakibi [+1] = Deplasmanın 1 Önceki Rakibi [-1]", "🔵");
        }

        if (c2A && c2B) {
            recordMatch(url, h.teamName, a.teamName, Set.of(h.prev2, h.next2),
                    "🔥 KUSURSUZ X ÇAPRAZ (Mesafe 2)", "Ev[-2] = Dep[+2] VE Ev[+2] = Dep[-2]", "🔥");
        } else {
            if (c2A) recordMatch(url, h.teamName, a.teamName, Set.of(h.prev2),
                    "YENİ PATTERN (2. Mesafe Çapraz)", "Ev Sahibinin 2 Önceki Rakibi [-2] = Deplasmanın 2 Sonraki Rakibi [+2]", "🔵");
            if (c2B) recordMatch(url, h.teamName, a.teamName, Set.of(h.next2),
                    "YENİ PATTERN (2. Mesafe Çapraz)", "Ev Sahibinin 2 Sonraki Rakibi [+2] = Deplasmanın 2 Önceki Rakibi [-2]", "🔵");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // YENİ ÜÇLÜ AĞ PATTERNİ
    // ─────────────────────────────────────────────────────────────
    private static void checkTriplePattern(TableAnalysis home, TableAnalysis away, String matchUrl) {
        // Lig tanımlı değilse çık
        if (home.upcomingLeague == null || away.upcomingLeague == null) return;
        if (!home.upcomingLeague.equals(away.upcomingLeague)) return;
        String league = home.upcomingLeague;   // Aynı lig

        // Aynı ligdeki tamamlanmış maçları filtrele
        List<PastMatch> homeCompleted = home.pastMatches.stream()
                .filter(pm -> pm.completed && pm.league.equals(league))
                .toList();
        List<PastMatch> awayCompleted = away.pastMatches.stream()
                .filter(pm -> pm.completed && pm.league.equals(league))
                .toList();

        // Ev = Z (sonra X ile oynar), Dep = Y (önce X ile oynar)
        // Ortak rakip adayları
        Set<String> homeOpponents = new HashSet<>();
        for (PastMatch pm : homeCompleted) {
            String opp = pm.homeTeam.equals(home.teamName) ? pm.awayTeam : pm.homeTeam;
            homeOpponents.add(opp);
        }
        Set<String> awayOpponents = new HashSet<>();
        for (PastMatch pm : awayCompleted) {
            String opp = pm.homeTeam.equals(away.teamName) ? pm.awayTeam : pm.homeTeam;
            awayOpponents.add(opp);
        }

        Set<String> commonX = new HashSet<>(homeOpponents);
        commonX.retainAll(awayOpponents);

        for (String x : commonX) {
            // Y'nin X ile maçı (dep'in, yani awayCompleted'da)
            PastMatch matchY_X = null;
            int indexY = -1;
            for (int i = 0; i < awayCompleted.size(); i++) {
                PastMatch pm = awayCompleted.get(i);
                String opp = pm.homeTeam.equals(away.teamName) ? pm.awayTeam : pm.homeTeam;
                if (opp.equals(x)) {
                    matchY_X = pm;
                    indexY = i;
                    break;
                }
            }
            // Z'nin X ile maçı (ev'in, yani homeCompleted'da)
            PastMatch matchZ_X = null;
            int indexZ = -1;
            for (int i = 0; i < homeCompleted.size(); i++) {
                PastMatch pm = homeCompleted.get(i);
                String opp = pm.homeTeam.equals(home.teamName) ? pm.awayTeam : pm.homeTeam;
                if (opp.equals(x)) {
                    matchZ_X = pm;
                    indexZ = i;
                    break;
                }
            }

            // Y-X, Z-X'ten önce oynanmış olmalı (indeksleri küçük olan daha eski)
            if (matchY_X == null || matchZ_X == null) continue;
            if (indexY >= indexZ) continue;   // Y-X daha eski değilse olmaz

            // X takımının formunu, Z-X maçının sayfasından kontrol et
            if (matchZ_X.matchId.isEmpty()) continue;
            List<PastMatch> xPastMatches = fetchTeamPastMatches(matchZ_X.matchId, x);
            if (xPastMatches == null) continue;

            // X'in aynı ligdeki tamamlanmış maçları (kronolojik sıralı)
            List<PastMatch> xLeagueMatches = xPastMatches.stream()
                    .filter(pm -> pm.completed && pm.league.equals(league))
                    .toList();
            if (xLeagueMatches.size() < 2) continue;

            // Son iki lig maçı (en güncel ikisi)
            PastMatch lastMatch = xLeagueMatches.get(xLeagueMatches.size() - 1);
            PastMatch secondLast = xLeagueMatches.get(xLeagueMatches.size() - 2);

            String oppLast = lastMatch.homeTeam.equals(x) ? lastMatch.awayTeam : lastMatch.homeTeam;
            String oppSecond = secondLast.homeTeam.equals(x) ? secondLast.awayTeam : secondLast.homeTeam;

            // Sırasıyla önce Y'ye, sonra Z'ye karşı oynamış olmalı
            if (oppSecond.equals(away.teamName) && oppLast.equals(home.teamName)) {
                // Pattern tamam! Tahminle beraber kaydet
                String desc = String.format(
                        "Üçlü Ağ: %s → %s (önce), %s → %s (sonra), şimdi %s - %s oynanacak. Tahmin: 2/1 veya 1/2 (İY/MS)",
                        x, away.teamName, x, home.teamName, home.teamName, away.teamName);
                recordMatch(matchUrl, home.teamName, away.teamName, Set.of(x),
                        "🧩 ÜÇLÜ AĞ PATTERNİ (Lig: " + league + ")", desc, "🧩");
            }
        }
    }

    // Belirli bir maçın Head2Head sayfasından, verilen takımın formundaki PastMatch listesini döner
    private static List<PastMatch> fetchTeamPastMatches(String matchId, String teamName) {
        try {
            String url = "https://arsiv.mackolik.com/Match/Head2Head.aspx?id=" + matchId + "&s=1";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            Document doc = Jsoup.parse(resp.body());

            Elements forms = doc.select("div.md:has(div.detail-title:contains(Form Durumu))");
            for (Element form : forms) {
                String title = form.select(".detail-title").text();
                // Takım adı başlıkta geçiyorsa bu form X'indir
                if (title.contains(teamName)) {
                    // parseForm mantığını kullanalım ama sadece pastMatches için
                    Elements rows = form.select("tr.row, tr.row-2");
                    List<PastMatch> list = new ArrayList<>();
                    for (Element row : rows) {
                        Elements scoreLinks = row.select("td:nth-child(4) a");
                        if (scoreLinks.isEmpty()) continue;
                        String href = scoreLinks.first().attr("href");
                        String league = row.select("td:first-child a").text().trim();
                        String date = row.select("td:nth-child(2)").text().trim();
                        Element homeElem = row.select("td:nth-child(3) a[href*=/Takim/], td:nth-child(3) span.team").first();
                        Element awayElem = row.select("td:nth-child(5) a[href*=/Takim/], td:nth-child(5) span.team").first();
                        if (homeElem == null || awayElem == null) continue;
                        String homeTeam = homeElem.text().trim();
                        String awayTeam = awayElem.text().trim();
                        String scoreText = scoreLinks.first().text().trim();
                        boolean completed = scoreText.matches(".*\\d+.*-.*\\d+.*");
                        String mid = "";
                        Matcher idMatcher = Pattern.compile("/Mac/(\\d+)/").matcher(href);
                        if (idMatcher.find()) mid = idMatcher.group(1);
                        list.add(new PastMatch(league, date, mid, homeTeam, awayTeam, scoreText, completed));
                    }
                    return list;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    // Yardımcı Metodlar
    // ─────────────────────────────────────────────────────────────
    private static void recordMatch(String url, String home, String away,
                                    Set<String> common, String patternName,
                                    String matchDesc, String emoji) {
        String resultMsg = String.format(
                "%s [%s] %s vs %s\n   Açıklama: %s\n   Bulunan Eşleşme/Skor: %s\n   Taktik: 2/1 VEYA 1/2 Oyna!\n   Link: %s",
                emoji, patternName, home, away, matchDesc, common, url);
        matchedPatterns.add(resultMsg);
    }

    private static void printProgress(int completed, int total) {
        int percentage = (int) ((completed * 100.0) / total);
        System.out.print("\r  ✓ İlerleme: " + completed + "/" + total + " (" + percentage + "%)");
    }

    private static void printResults(long startTime) {
        System.out.println("\n\n==== BÜTÜN MAÇLARIN ANALİZİ TAMAMLANDI ====");
        System.out.println("\n=======================================================");
        System.out.println("🔥 SONUÇ: SİSTEM TAKTİKLERİNE UYAN MAÇLAR 🔥");
        System.out.println("=======================================================");

        if (matchedPatterns.isEmpty()) {
            System.out.println("❌ Maalesef bugün için patternlere uyan maç bulunamadı.");
        } else {
            for (String result : matchedPatterns) {
                System.out.println(result);
                System.out.println("-------------------------------------------------------");
            }
            System.out.println("\n✅ Toplam Bulunan Sinyal Sayısı: " + matchedPatterns.size());
        }
        System.out.println("=======================================================\n");

        long elapsed = System.currentTimeMillis() - startTime;
        long seconds = elapsed / 1000;
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0) {
            System.out.println("⏱️  Toplam çalışma süresi: " + minutes + " dakika " + secs + " saniye.");
        } else {
            System.out.println("⏱️  Toplam çalışma süresi: " + secs + " saniye.");
        }
        System.out.println("✓ Sistem başarılı şekilde kapatıldı.\n");
    }

    // ─────────────────────────────────────────────────────────────
    // Veri Modelleri
    // ─────────────────────────────────────────────────────────────
    static class PastMatch {
        String league;
        String date;
        String matchId;
        String homeTeam;
        String awayTeam;
        String score;
        boolean completed;

        PastMatch(String league, String date, String matchId, String homeTeam, String awayTeam, String score, boolean completed) {
            this.league = league;
            this.date = date;
            this.matchId = matchId;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.score = score;
            this.completed = completed;
        }
    }

    static class TableAnalysis {
        String teamName;
        String prev2, prev1, next1, next2;
        String last2Score, last1Score;
        Set<String> prevOpponents = new HashSet<>();
        Set<String> nextOpponents = new HashSet<>();
        List<PastMatch> pastMatches;      // YENİ
        String upcomingLeague;            // YENİ

        TableAnalysis(String teamName, String prev2, String prev1, String next1, String next2, String last2Score, String last1Score,
                      List<PastMatch> pastMatches, String upcomingLeague) {
            this.teamName = teamName;
            this.prev2 = prev2;
            this.prev1 = prev1;
            this.next1 = next1;
            this.next2 = next2;
            this.last2Score = last2Score;
            this.last1Score = last1Score;
            this.pastMatches = pastMatches;
            this.upcomingLeague = upcomingLeague;
            if (prev2 != null) prevOpponents.add(prev2);
            if (prev1 != null) prevOpponents.add(prev1);
            if (next1 != null) nextOpponents.add(next1);
            if (next2 != null) nextOpponents.add(next2);
        }
    }
}