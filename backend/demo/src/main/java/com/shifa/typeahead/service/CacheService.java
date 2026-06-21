package com.shifa.typeahead.service;

import com.shifa.typeahead.cache.CacheNode;
import com.shifa.typeahead.cache.ConsistentHashRing;
import org.springframework.stereotype.Service;

@Service
public class CacheService {

    private final ConsistentHashRing ring;

    public CacheService(ConsistentHashRing ring) {
        this.ring = ring;
    }

    public Object get(String key){

        CacheNode node = ring.getNode(key);

        return node.get(key);
    }

    public void put(String key,Object value){

        CacheNode node = ring.getNode(key);

        node.put(key,value);
    }
}