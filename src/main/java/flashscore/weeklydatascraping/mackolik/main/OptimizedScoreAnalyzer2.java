package flashscore.weeklydatascraping.mackolik.main;

import flashscore.weeklydatascraping.mackolik.TeamIdFinder;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
// import java.util.logging.Level; // SLF4J kullanılacak
// import java.util.logging.Logger; // SLF4J kullanılacak
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.PrintWriter;
// Diğer importlar aynı kalır...

import static flashscore.weeklydatascraping.mackolik.main.ModifiedScoreAnalyzer2.MatchPattern;
import static flashscore.weeklydatascraping.mackolik.main.ModifiedScoreAnalyzer2.MatchResult; // findScorePatternAndCollect için

public class OptimizedScoreAnalyzer2 {

    private static final String CONFIG_FILE = "mackolik_config.properties";
    private static Properties properties = new Properties();
    private static final Logger logger = LoggerFactory.getLogger(OptimizedScoreAnalyzer2.class);

    public static void main(String[] args) throws IOException {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            properties.load(fis);
            logger.info("{} dosyası başarıyla yüklendi.", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("{} dosyası yüklenirken hata oluştu.", CONFIG_FILE, e);
            return;
        }

        String csvOutputFile = properties.getProperty("mackolik_output_csv_file", "mackolik_results.csv");
        logger.info("Mackolik analiz sonuçları {} dosyasına yazılacak.", csvOutputFile);

        List<String> teamIds;
        try {
            teamIds = TeamIdFinder.readIdsFromFile(properties); // properties objesini geçir
        } catch (IOException e) {
            logger.error("Takım ID'leri dosyasından okuma hatası: {}", properties.getProperty("team_ids_file", "team_ids.txt"), e);
            return;
        }
        Collections.shuffle(teamIds);

        try (FileWriter fw = new FileWriter(csvOutputFile);
             PrintWriter pw = new PrintWriter(fw)) {

            pw.println("ArananTakimID,ArananEvSahibi1,ArananSkor1,ArananDeplasman1,ArananEvSahibi2,ArananSkor2,ArananDeplasman2," +
                       "BulunanSezon,BulunanEvSahibi1,BulunanSkor1,BulunanDeplasman1,BulunanIlkYariSkoru1," +
                       "BulunanEvSahibi2,BulunanSkor2,BulunanDeplasman2,BulunanIlkYariSkoru2," +
                       "OncekiMac,OncekiMacIlkYari,SonrakiMac,SonrakiMacIlkYari");

            String yearsStr = properties.getProperty("analysis_years", "2023,2022,2021");
            List<Integer> analysisYears = new ArrayList<>();
            for (String s : Arrays.asList(yearsStr.split(","))) {
                try {
                    analysisYears.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException e) {
                    logger.error("Yapılandırma dosyasındaki analiz yılı geçersiz: {}.", s, e);
                }
            }
            Collections.sort(analysisYears, Collections.reverseOrder());


            for (String idStr : teamIds) {
                final int currentId;
                try {
                    currentId = Integer.parseInt(idStr.trim());
                } catch (NumberFormatException e) {
                    logger.error("Geçersiz takım ID formatı: {}. Atlanıyor.", idStr, e);
                    continue;
                }

                WebDriver driver = null;
                try {
                    logger.info("{} ID'li takım için analiz başlatılıyor.", currentId);
                    System.setProperty("webdriver.chrome.driver", properties.getProperty("webdriver_path", "src\\chrome\\chromedriver.exe"));
                    ChromeOptions options = new ChromeOptions();
                    options.addArguments("--headless");
                    options.addArguments("--disable-gpu");
                    options.addArguments("--no-sandbox");
                    driver = new ChromeDriver(options);

                    MatchPattern currentPattern = MatchResult.findCurrentSeasonLastTwoMatches(currentId, driver, properties);

                    if (currentPattern == null) {
                        logger.warn("{} ID'li takım için mevcut sezon maç örüntüsü bulunamadı. Bu takım atlanıyor.", currentId);
                        // Driver null check'i ve quit() finally bloğunda yapılacak.
                        continue;
                    }
                    if (currentPattern.getFirstTwo() == null || currentPattern.getLastTwo() == null) {
                        logger.warn("{} ID'li takım için mevcut sezon maç örüntüsünde skorlar eksik. Atlanıyor.", currentId);
                        continue;
                    }

                    logger.info("Aranan skor paterni (Takım ID: {}):\n{} {} {}\n{} {} {}", currentId,
                            currentPattern.homeTeam1, currentPattern.score1, currentPattern.awayTeam1,
                            currentPattern.homeTeam2, currentPattern.score2, currentPattern.awayTeam2);

                    boolean foundMatchesInAnyYearForThisTeam = false;
                    for (int year : analysisYears) {
                        String yearsSeason = year + "/" + (year + 1);
                        logger.debug("{} ID'li takım için {} sezonu analizi başlıyor.", currentId, yearsSeason);

                        List<MatchResult> seasonMatches = MatchResult.findScorePatternAndCollect(currentPattern, yearsSeason, currentId, driver, properties);

                        if (!seasonMatches.isEmpty()) {
                            foundMatchesInAnyYearForThisTeam = true;
                            logger.info("{} ID'li takım için {} sezonunda {} eşleşme bulundu.", currentId, yearsSeason, seasonMatches.size());
                            for (MatchResult match : seasonMatches) {
                                pw.println(formatMatchResultToCsv(currentId, currentPattern, match));
                            }
                        }
                    }

                    if (!foundMatchesInAnyYearForThisTeam) {
                         logger.info("{} ID'li takım için geçmiş sezonlarda belirtilen örüntüyle eşleşen maç bulunamadı.", currentId);
                    }

                } catch (org.openqa.selenium.WebDriverException wde) {
                    logger.error("{} ID'li takım işlenirken WebDriver hatası oluştu. Bu takım atlanıyor.", currentId, wde);
                } catch (Exception e) {
                    logger.error("{} ID'li takım işlenirken genel bir hata oluştu. Bu takım atlanıyor.", currentId, e);
                } finally {
                    if (driver != null) {
                        driver.quit();
                        logger.debug("{} ID'li takım için WebDriver kapatıldı.", currentId); // Debug seviyesine çekildi.
                    }
                }
            }
            logger.info("Tüm takımların analizi tamamlandı. Sonuçlar {} dosyasına yazıldı.", csvOutputFile);

        } catch (IOException e) {
            logger.error("CSV dosyası ({}) yazılırken bir hata oluştu.", csvOutputFile, e);
            throw e;
        }
    }

    private static String escapeCsv(String data) {
        if (data == null) return "";
        String escapedData = data.replaceAll("\r\n|\n|\r", " "); // Replace newlines with space
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            escapedData = "\"" + escapedData.replace("\"", "\"\"") + "\"";
        }
        return escapedData;
    }

    private static String formatMatchResultToCsv(int teamId, MatchPattern searchedPattern, MatchResult foundMatch) {
        return String.join(",",
                escapeCsv(String.valueOf(teamId)),
                escapeCsv(searchedPattern.homeTeam1), escapeCsv(searchedPattern.score1), escapeCsv(searchedPattern.awayTeam1),
                escapeCsv(searchedPattern.homeTeam2), escapeCsv(searchedPattern.score2), escapeCsv(searchedPattern.awayTeam2),
                escapeCsv(foundMatch.season),
                escapeCsv(foundMatch.homeTeam), escapeCsv(foundMatch.score), escapeCsv(foundMatch.awayTeam), escapeCsv(foundMatch.firstMatchHTScore),
                escapeCsv(foundMatch.secondMatchHomeTeam), escapeCsv(foundMatch.secondMatchScore), escapeCsv(foundMatch.secondMatchAwayTeam), escapeCsv(foundMatch.secondMatchHTScore),
                escapeCsv(foundMatch.previousMatchScore), escapeCsv(foundMatch.previousHTScore),
                escapeCsv(foundMatch.nextMatchScore), escapeCsv(foundMatch.nextHTScore)
        );
    }
}