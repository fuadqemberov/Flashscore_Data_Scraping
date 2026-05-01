package analyzer.fs.model;

public record Country(String code, String name, String url) {
    @Override
    public String toString() {
        return name + " (" + code + ")";
    }
}
