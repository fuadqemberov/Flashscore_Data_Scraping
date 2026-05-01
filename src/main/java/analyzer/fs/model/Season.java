package analyzer.fs.model;

public record Season(String id, String name, String leagueId, String url) {
    @Override
    public String toString() {
        return name;
    }
}
