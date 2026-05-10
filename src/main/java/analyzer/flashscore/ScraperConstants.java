package analyzer.flashscore;

import java.util.*;
import java.util.stream.Collectors;

public class ScraperConstants {

    public static final int MAX_CONCURRENT_DRIVERS = 10;

    public static final String BASE_URL = "https://www.flashscore.co.uk/football/";
    public static final String MATCH_URL_PREFIX = "https://www.flashscore.co.uk/match/football/";

    public static final String ODDS_API_URL =
            "https://global.ds.lsapp.eu/odds/pq_graphql?_hash=oce&eventId=%s&projectId=5&geoIpCode=AZ&geoIpSubdivisionCode=AZBA";

    public static final int BET365_ID = 16;

    public static final String[][] ODDS_TABS = {
            {"1x2", "1X2"}, {"Over/Under", "Over/Under"}, {"Both teams", "Both teams to score"},
            {"Double chance", "Double chance"}, {"Correct score", "Correct score"}
    };
    public static final String[] HALF_TABS = {"Full Time", "1st Half", "2nd Half"};

    // GÜNCELLENDİ: 5.5 eklendi
    public static final List<Double> OU_THRESHOLDS = Arrays.asList(0.5, 1.5, 2.5, 3.5, 4.5, 5.5);

    public static final List<String> CORRECT_SCORES = Arrays.asList(
            "1:0", "0:1", "1:1", "2:0", "0:2", "2:1", "1:2", "2:2", "3:0", "0:3",
            "3:1", "1:3", "3:2", "2:3", "3:3",
            "4:0", "0:4", "4:1", "1:4", "4:2", "2:4", "4:3", "3:4", "4:4"
    );

    // Sütun tanımları (öncekiyle aynı, HT/FT ve 5.5 zaten vardı)
    public static class ColumnDef {
        public final String groupLabel;
        public final String subLabel;
        public final String dataKey;

        public ColumnDef(String groupLabel, String subLabel, String dataKey) {
            this.groupLabel = groupLabel;
            this.subLabel = subLabel;
            this.dataKey = dataKey;
        }
    }

    public static final List<ColumnDef> COLUMN_DEFS = List.of(
            // MAÇ SONU
            new ColumnDef("MAÇ SONU", "1-A", "1x2|Full Time|Home"),
            new ColumnDef("MAÇ SONU", "X-A", "1x2|Full Time|Draw"),
            new ColumnDef("MAÇ SONU", "2-A", "1x2|Full Time|Away"),
            // İLK YARI
            new ColumnDef("İLK YARI", "1-A", "1x2|1st Half|Home"),
            new ColumnDef("İLK YARI", "X-A", "1x2|1st Half|Draw"),
            new ColumnDef("İLK YARI", "2-A", "1x2|1st Half|Away"),
            // İKİNCİ YARI
            new ColumnDef("İKİNCİ YARI", "1-A", "1x2|2nd Half|Home"),
            new ColumnDef("İKİNCİ YARI", "X-A", "1x2|2nd Half|Draw"),
            new ColumnDef("İKİNCİ YARI", "2-A", "1x2|2nd Half|Away"),
            // KG VAR/YOK
            new ColumnDef("MAÇ SONU KG VAR/YOK", "VAR-A", "Both teams|Full Time|Yes"),
            new ColumnDef("MAÇ SONU KG VAR/YOK", "YOK-A", "Both teams|Full Time|No"),
            new ColumnDef("İLK YARI KG VAR/YOK", "VAR-A", "Both teams|1st Half|Yes"),
            new ColumnDef("İLK YARI KG VAR/YOK", "YOK-A", "Both teams|1st Half|No"),
            new ColumnDef("İKİNCİ YARI KG VAR/YOK", "VAR-A", "Both teams|2nd Half|Yes"),
            new ColumnDef("İKİNCİ YARI KG VAR/YOK", "YOK-A", "Both teams|2nd Half|No"),
            // ÇİFTE ŞANS
            new ColumnDef("MAÇ SONU ÇİFTE ŞANS", "1XÇŞ-A", "Double chance|Full Time|1X"),
            new ColumnDef("MAÇ SONU ÇİFTE ŞANS", "12ÇŞ-A", "Double chance|Full Time|12"),
            new ColumnDef("MAÇ SONU ÇİFTE ŞANS", "X2ÇŞ-A", "Double chance|Full Time|X2"),
            new ColumnDef("İLK YARI ÇİFTE ŞANS", "1XÇŞ-A", "Double chance|1st Half|1X"),
            new ColumnDef("İLK YARI ÇİFTE ŞANS", "12ÇŞ-A", "Double chance|1st Half|12"),
            new ColumnDef("İLK YARI ÇİFTE ŞANS", "X2ÇŞ-A", "Double chance|1st Half|X2"),
            // MAÇ SONU ALT/ÜST (0.5 - 5.5)
            new ColumnDef("MAÇ SONU 0,5", "UST-A", "Over/Under|Full Time|O 0.5"),
            new ColumnDef("MAÇ SONU 0,5", "ALT-A", "Over/Under|Full Time|U 0.5"),
            new ColumnDef("MAÇ SONU 1,5", "UST-A", "Over/Under|Full Time|O 1.5"),
            new ColumnDef("MAÇ SONU 1,5", "ALT-A", "Over/Under|Full Time|U 1.5"),
            new ColumnDef("MAÇ SONU 2,5", "UST-A", "Over/Under|Full Time|O 2.5"),
            new ColumnDef("MAÇ SONU 2,5", "ALT-A", "Over/Under|Full Time|U 2.5"),
            new ColumnDef("MAÇ SONU 3,5", "UST-A", "Over/Under|Full Time|O 3.5"),
            new ColumnDef("MAÇ SONU 3,5", "ALT-A", "Over/Under|Full Time|U 3.5"),
            new ColumnDef("MAÇ SONU 4,5", "UST-A", "Over/Under|Full Time|O 4.5"),
            new ColumnDef("MAÇ SONU 4,5", "ALT-A", "Over/Under|Full Time|U 4.5"),
            new ColumnDef("MAÇ SONU 5,5", "UST-A", "Over/Under|Full Time|O 5.5"),
            new ColumnDef("MAÇ SONU 5,5", "ALT-A", "Over/Under|Full Time|U 5.5"),
            // İLK YARI ALT/ÜST (sadece 0.5, 1.5, 2.5)
            new ColumnDef("İLK YARI 0,5", "UST-A", "Over/Under|1st Half|O 0.5"),
            new ColumnDef("İLK YARI 0,5", "ALT-A", "Over/Under|1st Half|U 0.5"),
            new ColumnDef("İLK YARI 1,5", "UST-A", "Over/Under|1st Half|O 1.5"),
            new ColumnDef("İLK YARI 1,5", "ALT-A", "Over/Under|1st Half|U 1.5"),
            new ColumnDef("İLK YARI 2,5", "UST-A", "Over/Under|1st Half|O 2.5"),
            new ColumnDef("İLK YARI 2,5", "ALT-A", "Over/Under|1st Half|U 2.5"),
            // İKİNCİ YARI ALT/ÜST (sadece 0.5, 1.5, 2.5)
            new ColumnDef("İKİNCİ YARI 0,5", "UST-A", "Over/Under|2nd Half|O 0.5"),
            new ColumnDef("İKİNCİ YARI 0,5", "ALT-A", "Over/Under|2nd Half|U 0.5"),
            new ColumnDef("İKİNCİ YARI 1,5", "UST-A", "Over/Under|2nd Half|O 1.5"),
            new ColumnDef("İKİNCİ YARI 1,5", "ALT-A", "Over/Under|2nd Half|U 1.5"),
            new ColumnDef("İKİNCİ YARI 2,5", "UST-A", "Over/Under|2nd Half|O 2.5"),
            new ColumnDef("İKİNCİ YARI 2,5", "ALT-A", "Over/Under|2nd Half|U 2.5"),
            // HT/FT (İLK YARI / MAÇ SONU) – yeni veri kaynağından beslenecek
            new ColumnDef("İLK YARI / MAÇ SONU", "1/1-A", "HTFT|1/1"),
            new ColumnDef("İLK YARI / MAÇ SONU", "1/X-A", "HTFT|1/X"),
            new ColumnDef("İLK YARI / MAÇ SONU", "1/2-A", "HTFT|1/2"),
            new ColumnDef("İLK YARI / MAÇ SONU", "X/1-A", "HTFT|X/1"),
            new ColumnDef("İLK YARI / MAÇ SONU", "X/X-A", "HTFT|X/X"),
            new ColumnDef("İLK YARI / MAÇ SONU", "X/2-A", "HTFT|X/2"),
            new ColumnDef("İLK YARI / MAÇ SONU", "2/1-A", "HTFT|2/1"),
            new ColumnDef("İLK YARI / MAÇ SONU", "2/X-A", "HTFT|2/X"),
            new ColumnDef("İLK YARI / MAÇ SONU", "2/2-A", "HTFT|2/2"),
            // İLK YARI SKOR
            new ColumnDef("İLK YARI SKOR", "1:0-A", "Correct score|1st Half|1:0"),
            new ColumnDef("İLK YARI SKOR", "2:0-A", "Correct score|1st Half|2:0"),
            new ColumnDef("İLK YARI SKOR", "2:1-A", "Correct score|1st Half|2:1"),
            new ColumnDef("İLK YARI SKOR", "3:0-A", "Correct score|1st Half|3:0"),
            new ColumnDef("İLK YARI SKOR", "3:1-A", "Correct score|1st Half|3:1"),
            new ColumnDef("İLK YARI SKOR", "3:2-A", "Correct score|1st Half|3:2"),
            new ColumnDef("İLK YARI SKOR", "0:0-A", "Correct score|1st Half|0:0"),
            new ColumnDef("İLK YARI SKOR", "1:1-A", "Correct score|1st Half|1:1"),
            new ColumnDef("İLK YARI SKOR", "2:2-A", "Correct score|1st Half|2:2"),
            new ColumnDef("İLK YARI SKOR", "0:1-A", "Correct score|1st Half|0:1"),
            new ColumnDef("İLK YARI SKOR", "0:2-A", "Correct score|1st Half|0:2"),
            new ColumnDef("İLK YARI SKOR", "1:2-A", "Correct score|1st Half|1:2"),
            new ColumnDef("İLK YARI SKOR", "0:3-A", "Correct score|1st Half|0:3"),
            new ColumnDef("İLK YARI SKOR", "1:3-A", "Correct score|1st Half|1:3"),
            new ColumnDef("İLK YARI SKOR", "2:3-A", "Correct score|1st Half|2:3"),
            // MAÇ SONU SKOR
            new ColumnDef("MAÇ SONU SKOR", "1:0-A", "Correct score|Full Time|1:0"),
            new ColumnDef("MAÇ SONU SKOR", "2:0-A", "Correct score|Full Time|2:0"),
            new ColumnDef("MAÇ SONU SKOR", "2:1-A", "Correct score|Full Time|2:1"),
            new ColumnDef("MAÇ SONU SKOR", "3:0-A", "Correct score|Full Time|3:0"),
            new ColumnDef("MAÇ SONU SKOR", "3:1-A", "Correct score|Full Time|3:1"),
            new ColumnDef("MAÇ SONU SKOR", "3:2-A", "Correct score|Full Time|3:2"),
            new ColumnDef("MAÇ SONU SKOR", "4:0-A", "Correct score|Full Time|4:0"),
            new ColumnDef("MAÇ SONU SKOR", "4:1-A", "Correct score|Full Time|4:1"),
            new ColumnDef("MAÇ SONU SKOR", "4:2-A", "Correct score|Full Time|4:2"),
            new ColumnDef("MAÇ SONU SKOR", "4:3-A", "Correct score|Full Time|4:3"),
            new ColumnDef("MAÇ SONU SKOR", "5:0-A", "Correct score|Full Time|5:0"),
            new ColumnDef("MAÇ SONU SKOR", "5:1-A", "Correct score|Full Time|5:1"),
            new ColumnDef("MAÇ SONU SKOR", "5:2-A", "Correct score|Full Time|5:2"),
            new ColumnDef("MAÇ SONU SKOR", "0:0-A", "Correct score|Full Time|0:0"),
            new ColumnDef("MAÇ SONU SKOR", "1:1-A", "Correct score|Full Time|1:1"),
            new ColumnDef("MAÇ SONU SKOR", "2:2-A", "Correct score|Full Time|2:2"),
            new ColumnDef("MAÇ SONU SKOR", "3:3-A", "Correct score|Full Time|3:3"),
            new ColumnDef("MAÇ SONU SKOR", "4:4-A", "Correct score|Full Time|4:4"),
            new ColumnDef("MAÇ SONU SKOR", "0:1-A", "Correct score|Full Time|0:1"),
            new ColumnDef("MAÇ SONU SKOR", "0:2-A", "Correct score|Full Time|0:2"),
            new ColumnDef("MAÇ SONU SKOR", "1:2-A", "Correct score|Full Time|1:2"),
            new ColumnDef("MAÇ SONU SKOR", "0:3-A", "Correct score|Full Time|0:3"),
            new ColumnDef("MAÇ SONU SKOR", "1:3-A", "Correct score|Full Time|1:3"),
            new ColumnDef("MAÇ SONU SKOR", "2:3-A", "Correct score|Full Time|2:3"),
            new ColumnDef("MAÇ SONU SKOR", "0:4-A", "Correct score|Full Time|0:4"),
            new ColumnDef("MAÇ SONU SKOR", "1:4-A", "Correct score|Full Time|1:4"),
            new ColumnDef("MAÇ SONU SKOR", "2:4-A", "Correct score|Full Time|2:4"),
            new ColumnDef("MAÇ SONU SKOR", "3:4-A", "Correct score|Full Time|3:4"),
            new ColumnDef("MAÇ SONU SKOR", "0:5-A", "Correct score|Full Time|0:5"),
            new ColumnDef("MAÇ SONU SKOR", "1:5-A", "Correct score|Full Time|1:5"),
            new ColumnDef("MAÇ SONU SKOR", "2:5-A", "Correct score|Full Time|2:5")
    );

    public static final List<String> STATIC_COLUMN_KEYS = COLUMN_DEFS.stream()
            .map(cd -> cd.dataKey)
            .collect(Collectors.toList());
}