package flashscore.County;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class flashLinks {


    static List<String> county = new ArrayList<>();
    static List<String> liga = new ArrayList<>();


    public static void getCountryNames(WebDriver driver) throws InterruptedException {
        driver.get("https://www.flashscore.com/");
        Thread.sleep(1000);
        driver.findElement(By.xpath("//*[contains(text(), \"I Accept\")]")).click();
        Thread.sleep(500);
        driver.findElement(By.xpath("//*[contains(text(), \"Show more\")]")).click();
        Thread.sleep(500);

        List<WebElement> elementList = driver.findElements(By.xpath("//*[@id=\"category-left-menu\"]/div"));
        for(WebElement elem : elementList){
            List<WebElement> elementList1 =  elem.findElements(By.className("lmc__elementName"));
            for(WebElement elemen12 : elementList1){
                county.add(elemen12.getText());
            }
        }
        getLigaToTxt(driver,county);
    }

    public static void getLigaToTxt(WebDriver driver ,List<String> county){
        for(String name : county){
            try{
                driver.get("https://www.flashscore.com/football/"+name.toLowerCase());
                List<WebElement> links = driver.findElements(By.cssSelector(".leftMenu__item .leftMenu__href"));
                for (WebElement link : links) {
                    String hrefValue = link.getAttribute("href");
                    System.out.println(hrefValue);
                }
            }catch (Exception ex){
                continue;
            }

        }

    }

    public static void getLigaToTxt2(WebDriver driver, List<String> countries) {
        try (FileWriter writer = new FileWriter("ligas.txt", true)) {
            for (String name : countries) {
                try {
                    driver.get("https://www.flashscore.com/football/" + name.toLowerCase());
                    List<WebElement> links = driver.findElements(By.cssSelector(".leftMenu__item .leftMenu__href"));
                    for (WebElement link : links) {
                        String hrefValue = link.getAttribute("href");
                        System.out.println(hrefValue);
                        writer.write(hrefValue + System.lineSeparator());
                    }
                } catch (Exception ex) {
                    System.out.println("Error processing country: " + name);
                    ex.printStackTrace();
                    continue;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
