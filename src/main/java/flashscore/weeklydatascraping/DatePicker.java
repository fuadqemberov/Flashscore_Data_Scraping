package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


import static flashscore.Util.flashUtill.getChromeDriver;

public class DatePicker {
   public static List<String> mackolikId = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException, IOException {

        System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");
        WebDriver driver = getChromeDriver();
        //driver.get("https://arsiv.mackolik.com/Canli-Sonuclar");
        //idFromWebToTxt(driver);

        for(String id : readFileToString()){
            String link = "https://arsiv.mackolik.com/Mac/"+id+"/#karsilastirma";
            driver.get(link);
            String date = driver.findElement(By.xpath("//div[@class='match-info-date']")).getText().substring(8, 13);
            //for
                }


    //main
    }

    public static void  getDataFromWeb(WebDriver driver,String link){
        driver.get(link);
        driver.findElement(By.xpath("//*[@id=\"dvHTScoreText\"]"));
        driver.findElement(By.xpath("//*[@id=\"dvScoreText\"]"));
    }

    public static List<String> readFileToString(){
        String dosyaYolu = "mackolik_ids.txt"; // Dosyanızın adını ve yolunu buraya yazın
        List<String> satirlar = new ArrayList<>();
        List<String> result = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(dosyaYolu))) {
            String satir;
            while ((satir = br.readLine()) != null) {
                satirlar.add(satir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Şimdi satırları for-each döngüsü ile işleyebiliriz
        for (String s : satirlar) {
            result.add(s);
        }
        return result;
    }


    public static void idFromWebToTxt(WebDriver driver) throws InterruptedException, IOException {
        WebElement datePicker = driver.findElement(By.id("txtCalendar"));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String desiredDate;
        for (int i = 8; i < 10; i++) {
            for (int j = 1; j < 32; j++) {
                if(i==9 && j==7){
                    break;
                }
                desiredDate = j + "/" + i + "/2024";
                js.executeScript("arguments[0].value='" + desiredDate + "';", datePicker);
                js.executeScript("arguments[0].dispatchEvent(new Event('change'));", datePicker);
                Thread.sleep(2000);
                List<WebElement> mac = driver.findElements(By.xpath("//tr[starts-with(@id, 'row_')]"));
                Thread.sleep(500);
                for (WebElement elem : mac) {
                    try{
                        String rowId = elem.getAttribute("id");
                        String extractedId = rowId.split("row_")[1];
                        mackolikId.add(extractedId);
                    }
                    catch (Exception exception){
                        System.out.println(exception.getMessage());
                    }

                }
                Thread.sleep(500);
            }
        }

        Path filePath = Paths.get("mackolik_ids.txt");
        Files.write(filePath, DatePicker.mackolikId);
        System.out.println("Mackolik ID'leri başarıyla dosyaya yazıldı: " + filePath.toAbsolutePath());
    }



}
