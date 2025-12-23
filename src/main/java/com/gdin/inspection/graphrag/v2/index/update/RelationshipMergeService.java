package com.gdin.inspection.graphrag.v2.index.update;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RelationshipMergeService {

    public List<Relationship> merge(List<Relationship> oldRels, List<Relationship> deltaRels) {
        if (CollectionUtil.isEmpty(deltaRels)) return oldRels;

        int oldMax = oldRels.stream()
                .map(Relationship::getHumanReadableId)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
        int next = oldMax + 1;
        for (Relationship dr : deltaRels) {
            if (dr == null) continue;
            dr.setHumanReadableId(next++);
        }

        List<Relationship> merged = concat(oldRels, deltaRels);

        // groupby(source,target)
        Map<Key, List<Relationship>> grouped = new LinkedHashMap<>();
        for (Relationship r : merged) {
            if (r == null) continue;
            if (r.getSource() == null || r.getTarget() == null) continue;
            grouped.computeIfAbsent(new Key(r.getSource(), r.getTarget()), k -> new ArrayList<>()).add(r);
        }

        List<Relationship> aggregated = new ArrayList<>(grouped.size());
        for (Map.Entry<Key, List<Relationship>> e : grouped.entrySet()) {
            Key key = e.getKey();
            List<Relationship> items = e.getValue();
            Relationship first = items.get(0);

            // description list(str) -> join
            String desc = items.stream()
                    .map(Relationship::getDescription)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining("\n"));

            // text_unit_ids chain
            List<String> textUnitIds = items.stream()
                    .map(Relationship::getTextUnitIds)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // weight mean
            double weightMean = items.stream()
                    .map(Relationship::getWeight)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            // combined_degree sum (temporary, will be overwritten)
            double combinedDegreeSum = items.stream()
                    .map(Relationship::getCombinedDegree)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            Relationship out = Relationship.builder()
                    .id(first.getId())
                    .humanReadableId(first.getHumanReadableId())
                    .source(key.source)
                    .target(key.target)
                    .description(desc)
                    .weight(weightMean)
                    .combinedDegree(combinedDegreeSum)
                    .textUnitIds(textUnitIds)
                    .build();

            aggregated.add(out);
        }

        // recalc source_degree / target_degree and combined_degree = source_degree + target_degree
        Map<String, Long> sourceDegree = aggregated.stream()
                .collect(Collectors.groupingBy(Relationship::getSource, LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> targetDegree = aggregated.stream()
                .collect(Collectors.groupingBy(Relationship::getTarget, LinkedHashMap::new, Collectors.counting()));

        for (Relationship r : aggregated) {
            long sd = sourceDegree.getOrDefault(r.getSource(), 0L);
            long td = targetDegree.getOrDefault(r.getTarget(), 0L);
            r.setCombinedDegree((double) (sd + td));
        }

        aggregated.sort(Comparator.comparingInt(r -> r.getHumanReadableId() == null ? -1 : r.getHumanReadableId()));
        return aggregated;
    }

    private static List<Relationship> concat(List<Relationship> a, List<Relationship> b) {
        List<Relationship> out = new ArrayList<>((a == null ? 0 : a.size()) + (b == null ? 0 : b.size()));
        if (a != null) out.addAll(a);
        if (b != null) out.addAll(b);
        return out;
    }

    private static final class Key {
        final String source;
        final String target;

        Key(String source, String target) {
            this.source = source;
            this.target = target;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(source, k.source) && Objects.equals(target, k.target);
        }

        @Override public int hashCode() {
            return Objects.hash(source, target);
        }
    }
}
