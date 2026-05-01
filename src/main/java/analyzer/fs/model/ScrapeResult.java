package analyzer.fs.model;

import java.util.List;
import java.util.Map;

public record ScrapeResult(
    List<Country> countries,
    List<League> leagues,
    List<Season> seasons,
    List<Match> matches,
    Map<String, Odds> oddsMap
) {}
