package com.shifa.typeahead.controller;

import com.shifa.typeahead.dto.SearchRequest;
import com.shifa.typeahead.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @PostMapping("/search")
    public Map<String,String> search(
            @RequestBody SearchRequest request
    ) {

        service.search(request.getQuery());

        return Map.of(
                "message",
                "Searched"
        );
    }
}