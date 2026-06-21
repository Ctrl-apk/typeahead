package com.shifa.typeahead.service;

import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final BatchWriteService batchWriteService;

    public SearchService(
            BatchWriteService batchWriteService
    ) {
        this.batchWriteService = batchWriteService;
    }

    public void search(String query) {

        batchWriteService.recordSearch(query);

        System.out.println(
                "Buffered search: " + query
        );
    }
}