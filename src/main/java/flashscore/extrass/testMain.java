package flashscore.extrass;


import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import static flashscore.Data.flashDataScraper.datas;
import static flashscore.Data.flashDataScraper.getOddsDataFromJson;
import static flashscore.MatchID.flashMatchId.getMatchIdsFromFile;
import static flashscore.MatchID.flashMatchId.getMatchIdsFromSite;
import static flashscore.Util.flashUtill.*;


public class testMain {
    public static void main(String[] args) throws InterruptedException, IOException {
        Scanner sc = new Scanner(System.in);
        System.out.print("Hansi ilden sonraki datani isteyirsiniz daxil edin  : ");
        int year = sc.nextInt();
        WebDriver driver = getChromeDriver();
        String ligaFile = "src\\ligas.txt";
        String idFile = "matchIds.txt";
        List<String> urls = readUrlsFromFile(ligaFile);
        getMatchIdsFromSite(driver,urls.get(37),year);
        List<String> ids = getMatchIdsFromFile(idFile);
        for(int i =0;i<ids.size();i++){
            getOddsDataFromJson(ids.get(i));
            System.out.println(datas);
        }
        exceleYazdir();
    }
}
