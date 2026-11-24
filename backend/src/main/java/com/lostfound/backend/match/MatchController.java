package com.lostfound.backend.match;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /** Public: posts that might be about the same object as this one, best first. */
    @GetMapping("/api/items/{id}/matches")
    public List<MatchResponse> matches(@PathVariable Long id, @RequestParam(defaultValue = "5") int limit) {
        return matchService.matchesFor(id, Math.clamp(limit, 1, 10));
    }
}
