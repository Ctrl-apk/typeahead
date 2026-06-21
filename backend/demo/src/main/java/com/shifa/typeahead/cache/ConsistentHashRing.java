package com.shifa.typeahead.cache;

import org.springframework.stereotype.Component;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Consistent hash ring with virtual nodes.
 * Each physical CacheNode gets VIRTUAL_NODES slots on the ring so that
 * load distributes more evenly when nodes are added/removed.
 */
@Component
public class ConsistentHashRing {

    /** Number of virtual nodes per physical node */
    private static final int VIRTUAL_NODES = 100;

    private final TreeMap<Integer, CacheNode> ring = new TreeMap<>();

    // Keep references so we can iterate physical nodes during invalidation
    private final CacheNode[] physicalNodes;

    public ConsistentHashRing() {
        physicalNodes = new CacheNode[]{
                new CacheNode("Node-1"),
                new CacheNode("Node-2"),
                new CacheNode("Node-3")
        };
        for (CacheNode node : physicalNodes) {
            addNode(node);
        }
        System.out.println("[ConsistentHashRing] Initialized with "
                + physicalNodes.length + " nodes × "
                + VIRTUAL_NODES + " virtual nodes = "
                + ring.size() + " ring slots");
    }

    private void addNode(CacheNode node) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            String virtualKey = node.getNodeName() + "#" + i;
            ring.put(Math.abs(virtualKey.hashCode()), node);
        }
    }

    /**
     * Clockwise lookup: find the first ring slot ≥ hash(key).
     * Wraps around to the first slot if none found (ring topology).
     */
    public CacheNode getNode(String key) {
        int hash = Math.abs(key.hashCode());
        SortedMap<Integer, CacheNode> tail = ring.tailMap(hash);
        Integer nodeHash = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        CacheNode node = ring.get(nodeHash);
        System.out.println("[ConsistentHashRing] key='" + key
                + "' hash=" + hash + " → " + node.getNodeName());
        return node;
    }

    /**
     * Invalidate a specific key across all nodes that might hold it.
     * In a real distributed setup this would be a network call; here we
     * invalidate just the responsible node (single-process simulation).
     */
    public void invalidate(String key) {
        getNode(key).invalidate(key);
    }

    /**
     * After a batch flush, invalidate cached suggestions for every query
     * that was written so that stale prefix results are not served.
     * We invalidate all keys whose prefix matches the first N chars of the
     * flushed query (covers all prefix lookups that include this query).
     */
    public void invalidatePrefixesFor(String query) {
        for (int len = 1; len <= query.length(); len++) {
            String prefix = query.substring(0, len);
            getNode(prefix).invalidate(prefix);
        }
    }

    public CacheNode[] getPhysicalNodes() {
        return physicalNodes;
    }
}