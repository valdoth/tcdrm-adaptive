package org.tcdrm.adaptive.data;

import org.tcdrm.adaptive.cloudsim.DataFragment;

import java.util.*;

/**
 * Legacy workload templates inspired by the original cloudsim-multicloud implementation.
 *
 * Simple: UE-origin queries accessing 3 relations (one per provider), repeated.
 * Complex: Inter-region queries accessing ~6 relations (two per provider),
 *          encouraging cross-region and inter-provider transfers.
 */
public final class LegacyWorkloadTemplates {
    private LegacyWorkloadTemplates() {}

    /** Returns a list of fragment index sets for simple queries. */
    public static List<int[]> generateSimple(List<DataFragment> frags, int count, long seed) {
        List<int[]> sets = new ArrayList<>();
        Map<String, List<Integer>> byProvider = groupByProvider(frags);

        for (int i = 0; i < count; i++) {
            List<Integer> sel = new ArrayList<>();
            for (String provider : byProvider.keySet()) {
                List<Integer> ids = byProvider.get(provider);
                if (!ids.isEmpty()) sel.add(ids.get(i % ids.size()));
            }
            // Ensure exactly 3 items (one per provider) if available
            while (sel.size() > 3) sel.remove(sel.size() - 1);
            sets.add(sel.stream().mapToInt(Integer::intValue).toArray());
        }
        return sets;
    }

    /** Returns a list of fragment index sets for complex queries. */
    public static List<int[]> generateComplex(List<DataFragment> frags, int count, long seed) {
        List<int[]> sets = new ArrayList<>();
        Map<String, Map<String, List<Integer>>> byProvRegion = groupByProviderRegion(frags);

        for (int i = 0; i < count; i++) {
            List<Integer> sel = new ArrayList<>();
            for (String provider : byProvRegion.keySet()) {
                Map<String, List<Integer>> byRegion = byProvRegion.get(provider);
                // pick up to two regions per provider if possible
                List<String> regions = new ArrayList<>(byRegion.keySet());
                Collections.sort(regions);
                for (int k = 0; k < Math.min(2, regions.size()); k++) {
                    List<Integer> ids = byRegion.get(regions.get((i + k) % regions.size()));
                    if (!ids.isEmpty()) sel.add(ids.get(i % ids.size()));
                }
            }
            // Trim to a reasonable size (e.g., 6 relations)
            while (sel.size() > 6) sel.remove(sel.size() - 1);
            if (sel.isEmpty()) {
                // fallback: include first few fragments
                for (int j = 0; j < Math.min(6, frags.size()); j++) sel.add(j);
            }
            sets.add(sel.stream().mapToInt(Integer::intValue).toArray());
        }
        return sets;
    }

    public static List<DataFragment> select(List<DataFragment> all, int[] idx) {
        List<DataFragment> out = new ArrayList<>(idx.length);
        for (int id : idx) if (id >= 0 && id < all.size()) out.add(all.get(id));
        return out;
    }

    private static Map<String, List<Integer>> groupByProvider(List<DataFragment> frags) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (int i = 0; i < frags.size(); i++) {
            DataFragment f = frags.get(i);
            map.computeIfAbsent(f.getPrimaryProvider(), k -> new ArrayList<>()).add(i);
        }
        return map;
    }

    private static Map<String, Map<String, List<Integer>>> groupByProviderRegion(List<DataFragment> frags) {
        Map<String, Map<String, List<Integer>>> map = new LinkedHashMap<>();
        for (int i = 0; i < frags.size(); i++) {
            DataFragment f = frags.get(i);
            map.computeIfAbsent(f.getPrimaryProvider(), k -> new LinkedHashMap<>())
               .computeIfAbsent(f.getPrimaryRegion(), k -> new ArrayList<>())
               .add(i);
        }
        return map;
    }
}
