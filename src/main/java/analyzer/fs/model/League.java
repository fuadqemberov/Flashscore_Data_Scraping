package analyzer.fs.model;

public record League(String id, String name, String countryCode, String url, String slug) {
    @Override
    public String toString() {
        return name + " [" + countryCode + "]";
    }
}
