package flashscore.extrass;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static flashscore.extrass.flashUtilold.getChromeDriver;

public class Main5 {

    static int count = 1;
    static double valuess = -1;
    static List<String> oneRowData = new ArrayList<>();
    public static List<List<String>> datas = new ArrayList<>();
    static List<String> temp;
    static WebDriver driver = getChromeDriver();


    public static void getOddsDataFromJson(String linkPart) throws IOException, JSONException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet request = new HttpGet("https://5.ds.lsapp.eu/pq_graphql?_hash=oce&eventId=" + linkPart + "&projectId=5&geoIpCode=AZ&geoIpSubdivisionCode=AZBA");

        try (CloseableHttpResponse response = httpClient.execute(request)) {


            String json = EntityUtils.toString(response.getEntity());
            JSONObject jsonObject = new JSONObject(json);

            JSONObject data = jsonObject.getJSONObject("data");
            JSONObject oddsData = data.getJSONObject("findOddsByEventId");
            JSONArray odds = oddsData.getJSONArray("odds");
            for(int j =0;j<47;j++){
                oneRowData.add("-");
            }

            for (int i = 0; i < odds.length(); i++) {
                JSONObject eventOdds = odds.getJSONObject(i);
                int bookmakerId = eventOdds.getInt("bookmakerId");

                if (bookmakerId == 16) {

                    String bettingScope = eventOdds.getString("bettingScope");
                    String bettingType = eventOdds.getString("bettingType");
                    JSONArray eventOddsItems = eventOdds.getJSONArray("odds");


                    for (int j = 0; j < eventOddsItems.length(); j++) {
                        JSONObject oddsItem = eventOddsItems.getJSONObject(j);

                        getFirstHalfHDA(oddsItem,bettingType,bettingScope);
                        getFullTimeHDA(oddsItem,bettingType,bettingScope);
                        getSecondHalfHDA(oddsItem,bettingType,bettingScope);
                        getDoubleChanceFullTime(oddsItem,bettingType,bettingScope);
                        getHandicapValue(oddsItem,bettingType,bettingScope);
                        getHandicapValueEurope(oddsItem,bettingType,bettingScope);
                        getHT_FT(oddsItem,bettingType,bettingScope);
                        getScore(oddsItem,bettingType,bettingScope);
                        getBothTeamScoreHT(oddsItem,bettingType,bettingScope);
                        getBothTeamScoreFT(oddsItem,bettingType,bettingScope);
                        getDrawNoBet(oddsItem,bettingType,bettingScope);
                        getFirstHalfOVerUnder(oddsItem,bettingType,bettingScope);

                    }
                }
            }
        }


        addToList(getWhatYouWant("/html/body/div[1]/div/div[3]/div/span[3]/a",driver,linkPart),31);//liga adi
        addToList(getWhatYouWant("/html/body/div[1]/div/div[4]/div[1]/div",driver,linkPart),32); //tarix saat
        addToList(getWhatYouWant("/html/body/div[1]/div/div[9]/div/div[1]/div[2]",driver,linkPart),33); // ht
        String a = driver.findElement(By.xpath("//*[@id=\"detail\"]/div[4]/div[3]/div/div[1]/span[1]")).getText() + " - " + driver.findElement(By.xpath("/html/body/div[1]/div/div[4]/div[3]/div/div[1]/span[3]")).getText();
        addToList(a,34); //ft
        System.out.println(a);
        addToList(getWhatYouWant("//*[@id=\"detail\"]/div[4]/div[2]/div[3]/div[2]/a",driver,linkPart),35); //ev
        addToList(getWhatYouWant("//*[@id=\"detail\"]/div[4]/div[4]/div[3]/div[1]/a",driver,linkPart),36); //qonag

        temp = new ArrayList<>(oneRowData);
        datas.add(temp);

        oneRowData.clear();
    }




    private static void getBothTeamScoreHT(JSONObject oddsItem, String bettingType, String bettingScope) {
        if(bettingType.equals("BOTH_TEAMS_TO_SCORE") && bettingScope.equals("FIRST_HALF")){
            String opening = oddsItem.getString("opening");
            if(count==1){
                addToList(opening,37);
                count++;
            }

            else if(count==2){
                addToList(opening,38);
                count=1;
            }
        }
    }

    private static void getDrawNoBet(JSONObject oddsItem, String bettingType, String bettingScope) {
        if(bettingType.equals("DRAW_NO_BET") && bettingScope.equals("FULL_TIME")){
            String opening = oddsItem.getString("opening");
            if(count==1){
                addToList(opening,41);
                count++;
            }
            else if(count==2){
                addToList(opening,42);
                count=1;
            }
        }
    }


    private static void getBothTeamScoreFT(JSONObject oddsItem, String bettingType, String bettingScope) {
        if(bettingType.equals("BOTH_TEAMS_TO_SCORE") && bettingScope.equals("FULL_TIME")){
            String opening = oddsItem.getString("opening");
            if(count==1){
                addToList(opening,39);
                count++;
            }
            else if(count==2){
                addToList(opening,40);
                count=1;
            }
        }
    }


    private static void getFirstHalfOVerUnder(JSONObject oddsItem,String type , String scope) throws JSONException {
        if (type.equals("OVER_UNDER") && scope.equals("FIRST_HALF")) {
            String value = getHandicapValue2(oddsItem);
            String opening = oddsItem.getString("opening");
            String selection = oddsItem.getString("selection");

            if(value.equals("0.5") && selection.equals("OVER")){
                addToList(opening,43);

            }else if(value.equals("0.5") && selection.equals("UNDER")){
                addToList(opening,44);
            }
            else if(value.equals("1.5") && selection.equals("OVER")){
                addToList(opening,45);
            }
            else if(value.equals("1.5") && selection.equals("UNDER")){
                addToList(opening,46);
            }
        }

    }

    private static String getWhatYouWant(String xpath , WebDriver driver,String link){
        driver.get("https://www.flashscore.com/match/"+link);
        System.out.println(driver.findElement(By.xpath(xpath)).getText());
        return driver.findElement(By.xpath(xpath)).getText();
    }


    private static void getHandicapValue(JSONObject oddsItem,String type , String scope) throws JSONException {
        if (type.equals("ASIAN_HANDICAP") && scope.equals("FIRST_HALF")) {
            String value = getHandicapValue2(oddsItem);
            String opening = oddsItem.getString("opening");

               if(value.equals(String.valueOf(valuess)) && count==1){
                   addToList(opening,12);
                    valuess = valuess * -1;
                    count++;
                }else if(value.equals(String.valueOf(valuess)) && count==2){
                   addToList(opening,13);
                    count = 1;
                    valuess = -1;

                }
        }

    }

    private static void getHandicapValueEurope(JSONObject oddsItem,String type , String scope) throws JSONException {


        if (type.equals("EUROPEAN_HANDICAP") && scope.equals("FULL_TIME")) {
            String value = getHandicapValue2(oddsItem);
            String opening = oddsItem.getString("opening");



            if(value.equals(String.valueOf(valuess)) && count==1){
                addToList(opening,14);
                count++;
            }else if(value.equals(String.valueOf(valuess)) && count==2){
                addToList(opening,15);
                count++;
            }
            else if(value.equals(String.valueOf(valuess)) && count==3){
                addToList(opening,16);
                count = 1;
            }
        }
    }

    private static void getHT_FT(JSONObject oddsItem,String type , String scope) throws JSONException {
        if (type.equals("HALF_FULL_TIME") && scope.equals("FULL_TIME")) {

            String opening = oddsItem.getString("opening");
            String winner = oddsItem.getString("winner");

            if(winner.equals("1/X")){
                addToList(opening,17);

            }else if(winner.equals("1/2")){
                addToList(opening,18);

            }
            else if(winner.equals("2/1")){
                addToList(opening,19);
            }
            else if(winner.equals("2/X")){
                addToList(opening,20);
            }
            else if(winner.equals("1/1")){
                addToList(opening,21);
            }
            else if(winner.equals("2/2")){
                addToList(opening,22);
            }
            else if(winner.equals("X/X")){
                addToList(opening,23);
            }
            else if(winner.equals("X/2")){
                addToList(opening,24);
            }
            else if(winner.equals("X/1")){
                addToList(opening,25);
            }
        }
    }

      private static void getScore(JSONObject oddsItem,String type , String scope) throws JSONException {


        if (type.equals("CORRECT_SCORE") && scope.equals("FULL_TIME")) {

            String opening = oddsItem.getString("opening");
            String score = oddsItem.getString("score");



            if(score.equals("2:1")){
                addToList(opening,26);

            }else if(score.equals("3:1")){
                addToList(opening,27);
            }
            else if(score.equals("1:0")){
                addToList(opening,28);

            }
            else if(score.equals("2:0")){
                addToList(opening,29);

            }else if(score.equals("4:1")){
                addToList(opening,30);

            }

        }
    }

    private static String getHandicapValue2(JSONObject oddsItem) throws JSONException {

        if (oddsItem.has("handicap")) {
            JSONObject handicapObject = oddsItem.getJSONObject("handicap");

            if (handicapObject.has("value")) {
                return handicapObject.getString("value");
            }
        }
        return "";
    }

    private static void getDoubleChanceFullTime(JSONObject oddsItem, String bettingType, String bettingScope) {
        if(bettingType.equals("DOUBLE_CHANCE") && bettingScope.equals("FULL_TIME")){
            String opening = oddsItem.getString("opening");
            if(count==1){
                addToList(opening,9);
                count++;
            }
            else if(count==2){
                addToList(opening,10);
                count++;
            }
            else if(count==3){
                addToList(opening,11);
                count=1;
            }
        }
    }

    private static void getFullTimeHDA(JSONObject oddsItem, String bettingType, String bettingScope) {
        if(bettingType.equals("HOME_DRAW_AWAY") && bettingScope.equals("FULL_TIME")){
            String opening = oddsItem.getString("opening");
            if(count==1){
                addToList(opening,3);
                count++;
            }
            else if(count==2){
                addToList(opening,4);
                count++;
            }
            else if(count==3){
                addToList(opening,5);
                count=1;
            }
        }
    }

    private static void getSecondHalfHDA(JSONObject oddsItem, String bettingType, String bettingScope) {
        if(bettingType.equals("HOME_DRAW_AWAY") && bettingScope.equals("SECOND_HALF")){
            String opening = oddsItem.getString("opening");
            if(count==1){
                addToList(opening,6);
                count++;
            }
            else if(count==2){
                addToList(opening,7);
                count++;
            }
            else if(count==3){
                addToList(opening,8);
                count=1;
            }
        }

    }


    private static void getFirstHalfHDA(JSONObject oddsItem, String bettingType, String bettingScope) {
        if(bettingType.equals("HOME_DRAW_AWAY") && bettingScope.equals("FIRST_HALF")){
            String opening = oddsItem.getString("opening");
            if(count==1){
                addToList(opening,0);
                count++;
            }
            else if(count==2){
                addToList(opening,1);
                count++;
            }
            else if(count==3){
                addToList(opening,2);
                count=1;
            }
        }
    }

    private static void addToList(String open,int index){
        if(Objects.nonNull(open) && !open.isEmpty()){
            oneRowData.remove(index);
            oneRowData.add(index,open);
        }
    }

}
