package flashscore.weeklydatascraping;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OddsportalCurlApiSolution {

    public static void main(String[] args) {


        String dataUrl = "https://www.oddsportal.com/feed/postmatch-score/1-bizcWzP7-yj6b6.dat";


        String matchUrl = "https://www.oddsportal.com/football/africa/africa-cup-of-nations-u20/nigeria-tunisia-bizcWzP7/";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            
            HttpGet mainPageRequest = new HttpGet(matchUrl);
            mainPageRequest.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");

            System.out.println("Bahis bürosu isimleri için ana sayfa HTML'i alınıyor...");
            String mainPageHtml = "";
            try (CloseableHttpResponse response = httpClient.execute(mainPageRequest)) {
                mainPageHtml = EntityUtils.toString(response.getEntity());
            }

            Map<String, String> bookmakerMap = new HashMap<>();
            Pattern bookiesPattern = Pattern.compile("\"providersNames\":(\\{.*?\\})");
            Matcher bookiesMatcher = bookiesPattern.matcher(mainPageHtml);
            if (bookiesMatcher.find()) {
                JSONObject bookiesJson = new JSONObject(bookiesMatcher.group(1));
                for (String key : bookiesJson.keySet()) {
                    bookmakerMap.put(key, bookiesJson.getString(key));
                }
                System.out.println(bookmakerMap.size() + " bahis bürosu ismi bulundu.");
            } else {
                System.out.println("Bahis bürosu isimleri bulunamadı. Devam ediliyor...");
            }

            // --- 2. ADIM: Oranları almak için API'ye cURL komutunu taklit eden istek ---
            HttpGet dataRequest = new HttpGet(dataUrl);

            // cURL komutundaki TÜM başlıkları ekliyoruz.
            dataRequest.addHeader("accept", "application/json, text/plain, */*");
            dataRequest.addHeader("accept-language", "en-US,en;q=0.9,tr;q=0.8,ha;q=0.7,fr;q=0.6,az;q=0.5");
            dataRequest.addHeader("priority", "u=1, i");
            dataRequest.addHeader("referer", matchUrl);
            dataRequest.addHeader("sec-ch-ua", "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"");
            dataRequest.addHeader("sec-ch-ua-mobile", "?0");
            dataRequest.addHeader("sec-ch-ua-platform", "\"Windows\"");
            dataRequest.addHeader("sec-fetch-dest", "empty");
            dataRequest.addHeader("sec-fetch-mode", "cors");
            dataRequest.addHeader("sec-fetch-site", "same-origin");
            dataRequest.addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
            dataRequest.addHeader("x-requested-with", "XMLHttpRequest");

            // *** EN ÖNEMLİ KISIM: Cookie (Çerez) başlığı ***
            // cURL komutundaki '-b' parametresinin içeriğini buraya yapıştırıyoruz.
            String cookieHeader = "_ga=GA1.1.926427997.1710589821; __gads=ID=1d7d05ff6a424c68:T=1725082801:RT=1725082801:S=ALNI_MZfGoTejb9j0ehFUVNYsgL3MmaaFQ; __gpi=UID=00000eae781d6ef5:T=1725082801:RT=1725082801:S=ALNI_MaR6lh4a09KYXkX_KhTwyoWP--rEw; op_user_login_hash=8c06dfda37873184ff200be9ffc47cac; op_user_logout=0; remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=eyJpdiI6IjYyK3hhYmF5T3pBa1NmQ3lwaHJ6aGc9PSIsInZhbHVlIjoiWUZFbERQWmpGUVNINWN3NVVLVTR2d0I1L3kyRElIRmpicGlIWlk0R2NwbzVlMWJaN3R5V3YvVnlhVCszT0xpdDgyOTB1SFpTeEozRDQrejhBK08ySlBGN2xvZHZJTVYzSEJWNHRTVHNyWklwZnJaTEM0bVhlUHFmelZDUHZlSU8vNHZVNW9PbTIxeU93VE13Zm9vVXNnPT0iLCJtYWMiOiI1N2I4MjA3YTkyODBjNjg2MmM5ZTA2OTk3ZTM5NDQ4OTM5Mjg5ZWJiZGM5Yzk5Y2EyMTNhN2UwMGQyZDA3YjNlIiwidGFnIjoiIn0%3D; op_cookie-test=ok; op_user_login_id=QklqSkFOcmt6Q1V4TndVTkFObDNGUT09OjorL69kYKiFeK1UkiCDOlch; cf_clearance=fg49zPecQq2IEEpnXQ21gdVMZBrXX8HoC4rV4B9mlfQ-1748079321-1.2.1.1-h2Rvm5XZJfoaBd8SCx9Z4GH59u7.tsXSOpJUM_v_qBJwegBR2nZDbvGH8wVDgAbCs6VyoSQZ1JkhGUIIUlpVEsEAc0IS.JxSo7pnnuI.LUf9sVP3QUL_L37NFYoFRk3bBiHVyS8BDUuQ_n1D71ntN9zfGKQ3EObF6iqAbLD2JeC1tlFXpbGzqRVSsX6OYAPx86GKsk4DXOY1BNJ2LmiMFD55Yjwedyv2HKLRIUMOnOy6Kk0MO9SCArNdLUz8SBuPSmm5dPgNw6766elC6jZGCYTlIfgUWGlAdWgxRA3WMlGtdQTiI_ZxM4qAHpdW27_ZJKB51zco4oup5HCwpG5I20uiie5cnG7rVMeNjp5JX4Q; OptanonAlertBoxClosed=2025-05-24T09:35:26.347Z; eupubconsent-v2=CQR6EWAQR6EWAAcABBENBsFsAP_gAAAAAChQLPtT_G__bWlr-T73aftkeYxP99h77sQxBgbJE-4FzLvW_JwXx2E5NAzatqIKmRIAu3TBIQNlHJDURVCgaogVryDMaEyUgTNKJ6BkiFMRI2NYCFxvm4tjeQCY5vp991c1mB-t7dr83dzyy4hHn3a5_2S1WJCdAYetDfv8bBOT-9IOd_x8v4v4_F7pE2-eS1n_pWvp7B9-Yls_9X299_bbff7PFcQuF_-_X_vf_n37v943n77v__gAAAFCQBAAaADKAPaAvMdAEABoAMoA9oC8yUAEBeZSAGADQA9oC8ygAEAZQAAA.f_wAAAAAAAAA; _clck=2pe9n4%7C2%7Cfx1%7C0%7C1970; _sg_b_n=1750789276563; _clsk=1kxdr0e%7C1750789276987%7C9%7C1%7Cz.clarity.ms%2Fcollect; OptanonConsent=isGpcEnabled=0&datestamp=Tue+Jun+24+2025+22%3A21%3A17+GMT%2B0400+(Azerbaijan+Standard+Time)&version=202503.2.0&browserGpcFlag=0&isIABGlobal=false&consentId=384cbb3b-1fc3-4466-8e5c-798b94c64686&interactionCount=1&landingPath=NotLandingPage&groups=C0001%3A1%2CC0002%3A1%2CC0004%3A1%2CV2STACK42%3A1&hosts=H194%3A1%2CH302%3A1%2CH236%3A1%2CH198%3A1%2CH230%3A1%2CH203%3A1%2CH286%3A1%2CH526%3A1%2CH16%3A1%2CH190%3A1%2CH21%3A1%2CH301%3A1%2CH303%3A1%2CH304%3A1%2CH99%3A1%2CH305%3A1%2CH593%3A1&genVendors=V2%3A1%2C&geolocation=AZ%3BBA&AwaitingReconsent=false&isAnonUser=1; _sg_b_v=3%3B3682%3B1750788812; _ga_5YY4JY41P1=GS2.1.s1750791812$o13$g0$t1750791812$j60$l0$h0";
            dataRequest.addHeader("Cookie", cookieHeader);

            System.out.println("\nOran verileri cURL taklidi ile API'den çekiliyor...");
            String rawJsResponse;
            try (CloseableHttpResponse response = httpClient.execute(dataRequest)) {
                rawJsResponse = EntityUtils.toString(response.getEntity());
            }

            // --- 3. ADIM: Gelen Yanıtı İşlemek ---
            if (!rawJsResponse.startsWith("___d(") || !rawJsResponse.endsWith(");")) {
                System.out.println("Beklenmedik API yanıt formatı. Yanıt şu şekilde başlıyor: " + (rawJsResponse.length() > 100 ? rawJsResponse.substring(0, 100) : rawJsResponse));
                return;
            }
            String jsonString = rawJsResponse.substring(5, rawJsResponse.length() - 2);
            JSONObject data = new JSONObject(jsonString);

            if (!data.has("d") || !data.getJSONObject("d").has("oddsdata") || !data.getJSONObject("d").getJSONObject("oddsdata").has("prematch")) {
                System.out.println("JSON yanıtı beklenen 'd.oddsdata.prematch' yapısını içermiyor.");
                return;
            }

            JSONArray prematchOdds = data.getJSONObject("d").getJSONObject("oddsdata").getJSONArray("prematch");

            System.out.println("----------------------------------------------------");

            for (int i = 0; i < prematchOdds.length(); i++) {
                JSONObject bookmakerData = prematchOdds.getJSONObject(i);

                // Sadece 1X2 oranlarını (betid=1) alıyoruz
                if (bookmakerData.getInt("betid") == 1) {
                    String bookmakerId = String.valueOf(bookmakerData.getInt("bookmakerid"));
                    String bookmakerName = bookmakerMap.getOrDefault(bookmakerId, "Bilinmeyen Büro (ID: " + bookmakerId + ")");
                    System.out.println("\nBahis Bürosu: " + bookmakerName);

                    JSONObject odds = bookmakerData.getJSONObject("odds");

                    double currentOdd1 = 0, openingOdd1 = 0;
                    double currentOddX = 0, openingOddX = 0;
                    double currentOdd2 = 0, openingOdd2 = 0;

                    Iterator<String> keys = odds.keys();
                    while(keys.hasNext()) {
                        String key = keys.next();
                        JSONObject oddInfo = odds.getJSONObject(key);
                        // 'name' alanı 1, X, 2 değerlerini içerir.
                        String oddName = oddInfo.optString("name", "");

                        if (oddName.equals("1")) {
                            currentOdd1 = oddInfo.getDouble("value");
                            openingOdd1 = oddInfo.getDouble("opening");
                        } else if (oddName.equals("X")) {
                            currentOddX = oddInfo.getDouble("value");
                            openingOddX = oddInfo.getDouble("opening");
                        } else if (oddName.equals("2")) {
                            currentOdd2 = oddInfo.getDouble("value");
                            openingOdd2 = oddInfo.getDouble("opening");
                        }
                    }

                    System.out.println("1 -> Güncel Oran: " + currentOdd1 + " | Açılış Oranı: " + openingOdd1);
                    System.out.println("X -> Güncel Oran: " + currentOddX + " | Açılış Oranı: " + openingOddX);
                    System.out.println("2 -> Güncel Oran: " + currentOdd2 + " | Açılış Oranı: " + openingOdd2);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}