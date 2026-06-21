package com.shifa.typeahead.controller;

import com.shifa.typeahead.model.SearchQuery;
import com.shifa.typeahead.service.SuggestService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class SuggestController {

    private final SuggestService service;

    public SuggestController(SuggestService service) {
        this.service = service;
    }

    @GetMapping("/suggest")
    public List<SearchQuery> suggest(
            @RequestParam String q
    ) {
        return service.getSuggestions(q);
    }

    @GetMapping("/trending")
    public List<SearchQuery> trending() {

        return service.getTrending();
    }
}