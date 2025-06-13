package flashscore.weeklydatascraping.mackolik;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeamIdFinder {

    private static final String CONFIG_FILE = "mackolik_config.properties";
    private static Properties properties = new Properties();
    private static final Logger logger = LoggerFactory.getLogger(TeamIdFinder.class);
    static List<String> teamIdList = new ArrayList<>();
    static List<String> leagueIdList = new ArrayList<>();
    static WebDriver driver; // Initialize after properties are loaded
    public static String txt; // Will be loaded from properties

    static {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            properties.load(fis);
            logger.info("{} dosyası başarıyla yüklendi.", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("{} dosyası yüklenirken hata oluştu.", CONFIG_FILE, e);
            // Consider re-throwing or exiting if config is critical
        }

        String leagueIdsStr = properties.getProperty("league_ids", "67483"); // Default if not in file
        leagueIdList.addAll(Arrays.asList(leagueIdsStr.split(",")));

        txt = properties.getProperty("team_ids_file", "team_ids.txt"); // Default if not in file

        // Initialize WebDriver after loading properties, especially if webdriver_path is needed here
        driver = initializeDriver();
    }

    public static void main(String[] args) {
        proccess();
    }

    public static void proccess() {
        try {
            for (String leagueId : leagueIdList) {
                String baseUrl = properties.getProperty("mackolik_arsiv_base_url", "https://arsiv.mackolik.com");
                String url1 = baseUrl + "/Puan-Durumu/" + leagueId + "/";
                String url2 = baseUrl + "/Puan-Durumu/s=" + leagueId + "/";
                driver.get(url1); // Removed redundant leagueId concatenation
                List<WebElement> teamLinks = driver.findElements(By.xpath("//a[contains(@class, 'style3')]"));
                logger.debug("{} URL'sinden {} takım linki bulundu.", url1, teamLinks.size());

                // Eğer hiçbir takım bulunamadıysa, alternatif URL'yi dene
                if (teamLinks.isEmpty()) {
                    logger.info("{} için takım bulunamadı, alternatif URL deneniyor: {}", leagueId, url2);
                    driver.get(url2);
                    teamLinks = driver.findElements(By.xpath("//a[contains(@class, 'style3')]"));
                    logger.debug("{} URL'sinden {} takım linki bulundu.", url2, teamLinks.size());
                }

                for (WebElement teamLink : teamLinks) {
                    String href = teamLink.getAttribute("href");
                    if (href != null && href.contains("/Team/Default.aspx?id=")) {
                        try {
                            String teamId = href.split("id=")[1].split("&")[0]; // More robust parsing
                            if (!teamIdList.contains(teamId)) { // Avoid duplicates
                                teamIdList.add(teamId);
                                logger.trace("Takım ID eklendi: {}", teamId);
                            }
                        } catch (Exception e) {
                            logger.warn("Takım ID'si parse edilirken hata oluştu. Href: {}", href, e);
                        }
                    } else {
                        logger.warn("Geçersiz takım linki formatı: {}", href);
                    }
                }
            }
            writeIdsToFile();
            logger.info("Tüm ligler için takım ID'leri işlendi. Toplam {} ID bulundu.", teamIdList.size());

        } catch (org.openqa.selenium.WebDriverException wde) {
            logger.error("TeamIdFinder.proccess içinde WebDriver hatası oluştu.", wde);
        } catch (Exception e) {
            logger.error("TeamIdFinder.proccess içinde beklenmedik bir hata oluştu.", e);
        } finally {
            if (driver != null) {
                driver.quit();
                logger.info("TeamIdFinder WebDriver kapatıldı.");
            }
        }
    }

    static WebDriver initializeDriver() {
        logger.info("TeamIdFinder için WebDriver başlatılıyor...");
        System.setProperty("webdriver.chrome.driver", properties.getProperty("webdriver_path", "src\\chrome\\chromedriver.exe"));
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        try {
            WebDriver newDriver = new ChromeDriver(options);
            logger.info("TeamIdFinder WebDriver başarıyla başlatıldı.");
            return newDriver;
        } catch (Exception e) {
            logger.error("TeamIdFinder WebDriver başlatılırken hata.", e);
            throw new RuntimeException("TeamIdFinder WebDriver başlatılamadı", e);
        }
    }

    static void writeIdsToFile() {
        if (txt == null || txt.isEmpty()) {
            logger.error("Takım ID'leri dosya adı (team_ids_file) yapılandırmada belirtilmemiş veya boş.");
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(txt))) {
            String ids = String.join(",", teamIdList); // Virgülle birleştir, boşluksuz
            writer.write(ids);
            logger.info("Takım ID'leri dosyaya yazıldı: {}. Toplam {} ID.", txt, teamIdList.size());
        } catch (IOException e) {
            logger.error("{} dosyasına takım ID'leri yazılırken hata oluştu.", txt, e);
        }
    }

    public static List<String> readIdsFromFile(Properties config) throws IOException { // Added Properties config
        String teamIdsFilePath = config.getProperty("team_ids_file", "team_ids.txt");
        logger.debug("{} dosyasından takım ID'leri okunuyor.", teamIdsFilePath);
        try (BufferedReader reader = new BufferedReader(new FileReader(teamIdsFilePath))) {
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                List<String> ids = Arrays.asList(line.split(",\\s*"));
                logger.info("{} dosyasından {} takım ID'si okundu.", teamIdsFilePath, ids.size());
                return ids;
            } else {
                logger.info("{} dosyası boş veya okunamadı.", teamIdsFilePath);
                return List.of();
            }
        } catch (IOException e) {
            logger.error("{} dosyasını okurken hata oluştu.", teamIdsFilePath, e);
            throw e;
        }
    }
}
