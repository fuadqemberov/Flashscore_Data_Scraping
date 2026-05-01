package analyzer.fs;

public class FlashscoreConfig {
    public static final String API_BASE = "https://d.flashscore.com/x/feed";
    public static final String DOMAIN = "https://www.flashscore.co.uk";
    public static final String DEFAULT_FSIGN = "SW9D1eZo";

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static final int CONNECT_TIMEOUT_SEC = 10;
    public static final int REQUEST_TIMEOUT_SEC = 15;
    public static final int BATCH_SIZE = 100;
    public static final int RATE_LIMIT_MS = 50;

    private FlashscoreConfig() {}
}
