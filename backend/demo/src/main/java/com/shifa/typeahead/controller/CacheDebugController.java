package com.shifa.typeahead.controller;

import com.shifa.typeahead.cache.CacheNode;
import com.shifa.typeahead.cache.ConsistentHashRing;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/cache")
public class CacheDebugController {

    private final ConsistentHashRing ring;

    public CacheDebugController(
            ConsistentHashRing ring
    ) {
        this.ring = ring;
    }

    @GetMapping("/debug")
    public Map<String,Object> debug(
            @RequestParam String prefix
    ) {

        CacheNode node =
                ring.getNode(prefix);

        boolean hit =
                node.get(prefix) != null;

        Map<String,Object> result =
                new HashMap<>();

        result.put("prefix", prefix);
        result.put("node", node.getNodeName());
        result.put("status", hit ? "HIT" : "MISS");

        return result;
    }
}