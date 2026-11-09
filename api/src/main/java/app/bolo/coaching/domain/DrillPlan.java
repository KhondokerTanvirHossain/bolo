package app.bolo.coaching.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Drills from a talk's fixes (DRL-1): one per fix, in fix order, at most 3. A sound fix → minimal pairs; the first upgrade →
 * say it better; a second upgrade → shadowing its better sentence; the short-answers habit → shadowing its model answer.
 * The filler habit makes no drill (it's practised in talks). Disputed fixes are already gone from the body.
 */
public final class DrillPlan {

    public static final int MAX = 3;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** listen: {a, b, target} rounds (target = the word with the pattern's sound); say: the 5 words to say. */
    public record Pairs(List<String[]> listen, List<String> say) {
    }

    @FunctionalInterface
    public interface PairsLookup {
        Optional<Pairs> pairs(String pattern);
    }

    private DrillPlan() {
    }

    public static String drills(JsonNode body, PairsLookup lookup) {
        ArrayNode out = JSON.createArrayNode();
        boolean sayBetterUsed = false;
        for (JsonNode fix : body.path("fixes")) {
            if (out.size() >= MAX) break;
            String kind = fix.path("kind").asString("");
            if ("sound".equals(kind)) {
                String pattern = fix.path("pattern").asString("");
                Optional<Pairs> pairs = lookup.pairs(pattern);
                if (pairs.isEmpty()) continue;
                ObjectNode d = out.addObject().put("key", "pairs:" + pattern).put("kind", "pairs").put("title", fix.path("title").asString(""))
                        .put("pattern", pattern).put("tip", fix.path("tip").asString(""));
                ArrayNode listen = d.putArray("listen");
                for (String[] p : pairs.get().listen()) listen.addObject().put("a", p[0]).put("b", p[1]).put("target", p[2]);
                ArrayNode say = d.putArray("say");
                pairs.get().say().forEach(say::add);
            } else if ("upgrade".equals(kind)) {
                String better = fix.path("better").asString("").trim();
                if (better.isEmpty()) continue;
                if (!sayBetterUsed) {
                    sayBetterUsed = true;
                    ObjectNode d = out.addObject().put("key", "sayBetter:" + better.toLowerCase()).put("kind", "sayBetter").put("title", "Say it better")
                            .put("said", fix.path("said").asString("")).put("better", better);
                    ArrayNode keys = d.putArray("keyWords");
                    for (JsonNode w : fix.path("changed")) keys.add(w.asString());
                } else {
                    out.addObject().put("key", "shadow:" + better.toLowerCase()).put("kind", "shadow").put("title", "Shadow it").put("sentence", better);
                }
            } else if ("habit".equals(kind) && fix.hasNonNull("better")) {
                String better = fix.path("better").asString("").trim();
                if (!better.isEmpty()) {
                    out.addObject().put("key", "shadow:" + better.toLowerCase()).put("kind", "shadow").put("title", "Longer answers").put("sentence", better);
                }
            }
        }
        return JSON.writeValueAsString(out);
    }

    /** Every word and sentence these drills may ask the Coach to say (the say endpoint speaks nothing else). */
    public static Set<String> texts(JsonNode drills) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode d : drills) {
            for (JsonNode r : d.path("listen")) { out.add(r.path("a").asString()); out.add(r.path("b").asString()); }
            for (JsonNode w : d.path("say")) out.add(w.asString());
            if (d.hasNonNull("better")) out.add(d.path("better").asString());
            if (d.hasNonNull("sentence")) out.add(d.path("sentence").asString());
        }
        out.remove("");
        return out;
    }
}
