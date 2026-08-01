package com.stylecast.occasion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministically splits free text (a whole {@code outfitRequest}
 * sentence, or a single already-extracted phrase that may itself merge
 * several distinct garments) into one or more recognized garment
 * phrase/category pairs, in reading order.
 *
 * <p>This exists to fix a confirmed bug: classifying an ENTIRE multi-word
 * phrase (e.g. {@code "shirt trousers shoes"}) by checking whether it
 * merely <em>contains</em> each category's keywords, in a fixed priority
 * order, silently picks whichever category happens to be checked first and
 * discards the rest - observed in production as the whole phrase
 * collapsing into a single {@code FOOTWEAR} item (because {@code "shoes"}
 * was checked before {@code "shirt"}/{@code "trousers"} ever got a chance).
 *
 * <p>Instead, this scans word by word, emitting a NEW item every time it
 * recognizes a garment "head noun" (e.g. {@code shirt}, {@code trousers},
 * {@code shoes}), bundling any preceding descriptive words (colors,
 * materials, activity terms - {@code white}, {@code leather}, {@code
 * soccer}) into that item's phrase as long as they don't themselves start a
 * new recognized item. Known two-word compounds (e.g. {@code "dress
 * shirt"}, {@code "swim trunks"}, {@code "pool slides"}) are checked first
 * at each position so they are never split apart - this is what makes
 * {@code "USA soccer jersey shorts soccer boots"} split into exactly
 * {@code "USA soccer jersey"} (TOP), {@code "shorts"} (BOTTOM), {@code
 * "soccer boots"} (FOOTWEAR) rather than 6 individual words.
 *
 * <p>Deterministic parsing only - never calls an LLM itself. Text with no
 * recognized keyword at all yields an empty list (never a guessed/invented
 * item); trailing words after the last recognized head noun that never
 * resolve to one are silently dropped for the same reason.
 */
final class RequestedItemPhraseSplitter {

    /** One recognized garment phrase and the category its head noun belongs to. */
    record SplitItem(String phrase, GenericItemCategory category) {
    }

    private RequestedItemPhraseSplitter() {
    }

    private static final Pattern LEADING_FILLER = Pattern.compile(
            "^(i want|i need|i'd like|i would like|i will wear|i'll wear|i am wearing|i'm wearing|"
                    + "planning to wear|looking for|need|want|wear|wearing|get me|bring|pack)\\s+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(a|an|the|some|my)\\s+", Pattern.CASE_INSENSITIVE);

    // ",\s*and\s+" (an Oxford-comma "and") is checked before the plain comma/and
    // alternatives so it consumes both together, avoiding a stray leading "and" on the
    // next segment (e.g. "...tie, and dress shoes" splits into "tie"/"dress shoes", not
    // "tie"/"and dress shoes"). Also supports "&", semicolons, and line breaks.
    private static final Pattern SEGMENT_SPLIT = Pattern.compile(
            "\\s*,\\s*and\\s+|\\s*,\\s*|\\s+and\\s+|\\s*&\\s*|\\s+with\\s+|\\s+plus\\s+|\\s+as well as\\s+|;\\s*|[\\r\\n]+",
            Pattern.CASE_INSENSITIVE);

    /**
     * Checked first at each tokenizer position (a 2-word window) so a known
     * compound is never split into its individual words, even though its
     * first word (e.g. "dress", which alone means {@link GenericItemCategory#ONE_PIECE})
     * would otherwise be misread as its own item.
     */
    private static final Map<String, GenericItemCategory> COMPOUND_OVERRIDES = Map.ofEntries(
            Map.entry("swim cap", GenericItemCategory.ACCESSORY),
            Map.entry("swimming cap", GenericItemCategory.ACCESSORY),
            Map.entry("swim trunks", GenericItemCategory.BOTTOM),
            Map.entry("swim goggles", GenericItemCategory.EQUIPMENT),
            Map.entry("swimming goggles", GenericItemCategory.EQUIPMENT),
            Map.entry("pool slides", GenericItemCategory.FOOTWEAR),
            Map.entry("football boots", GenericItemCategory.FOOTWEAR),
            Map.entry("soccer cleats", GenericItemCategory.FOOTWEAR),
            Map.entry("soccer boots", GenericItemCategory.FOOTWEAR),
            Map.entry("hiking boots", GenericItemCategory.FOOTWEAR),
            Map.entry("hiking shoes", GenericItemCategory.FOOTWEAR),
            Map.entry("hiking trousers", GenericItemCategory.BOTTOM),
            Map.entry("hiking pants", GenericItemCategory.BOTTOM),
            Map.entry("hiking shirt", GenericItemCategory.TOP),
            Map.entry("rain shell", GenericItemCategory.OUTERWEAR),
            Map.entry("dress shirt", GenericItemCategory.TOP),
            Map.entry("dress shoes", GenericItemCategory.FOOTWEAR),
            Map.entry("dress pants", GenericItemCategory.BOTTOM),
            Map.entry("formal shoes", GenericItemCategory.FOOTWEAR));

    // Single-word "head noun" keyword lists - category mapping per garment term.
    private static final List<String> ONE_PIECE_KEYWORDS =
            List.of("jumpsuit", "romper", "onesie", "wetsuit", "swimsuit", "bodysuit", "dress");
    private static final List<String> FOOTWEAR_KEYWORDS =
            List.of("boot", "shoe", "sneaker", "slide", "sandal", "flat", "heel", "loafer", "trainer", "cleat");
    private static final List<String> BOTTOM_KEYWORDS =
            List.of("short", "trouser", "pant", "skirt", "jean", "legging", "trunk");
    private static final List<String> OUTERWEAR_KEYWORDS =
            List.of("jacket", "coat", "shell", "parka", "blazer", "cardigan", "hoodie");
    private static final List<String> TOP_KEYWORDS =
            List.of("shirt", "jersey", "blouse", "sweater", "polo", "tee", "tank", "top", "jumper");
    private static final List<String> ACCESSORY_KEYWORDS =
            List.of("tie", "belt", "bag", "cap", "hat", "scarf", "glove", "sock", "sunglasses", "jewelry", "watch");
    private static final List<String> EQUIPMENT_KEYWORDS =
            List.of("goggle", "pad", "guard", "helmet", "ball", "racket", "club", "gear");
    // A full outfit/garment-set phrase (e.g. "navy suit") is a genuine, recognized
    // product phrase even though it isn't literally a single-piece garment - kept
    // separate from the "nothing matched" case (see classifySingleWord).
    private static final List<String> OTHER_KEYWORDS = List.of("suit", "tuxedo", "costume", "uniform");

    private static final Set<String> BARE_GENERIC_TERMS = Set.of(
            "shorts", "shirt", "boots", "shoes", "pants", "trousers", "jersey", "cap", "goggles",
            "jacket", "socks", "gloves", "trunks", "slides", "sandals");

    /**
     * Splits a whole free-text request (which may separate several garments
     * with commas/"and"/"&"/semicolons/line breaks) into recognized items.
     */
    static List<SplitItem> splitText(String text, String activityContext) {
        List<SplitItem> items = new ArrayList<>();
        for (String segment : splitIntoSegments(text)) {
            items.addAll(splitSegment(segment, activityContext));
        }
        return List.copyOf(items);
    }

    /**
     * Splits one already-isolated phrase (no comma/"and"/etc. separators
     * expected, but possibly still merging several garments with no
     * separator at all, e.g. {@code "shirt trousers shoes"}) into 1+
     * recognized garment items - used as a safety net over AI-provided
     * {@code originalPhrase} values, which occasionally merge distinct
     * garments the same way the deterministic fallback used to.
     */
    static List<SplitItem> splitPhrase(String phrase, String activityContext) {
        return splitSegment(phrase, activityContext);
    }

    private static List<String> splitIntoSegments(String text) {
        String trimmed = text.trim().replaceAll("[.!]+$", "");
        List<String> segments = new ArrayList<>();
        for (String raw : SEGMENT_SPLIT.split(trimmed)) {
            String segment = raw.trim();
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private static List<SplitItem> splitSegment(String rawSegment, String activityContext) {
        String stripped = LEADING_FILLER.matcher(rawSegment.trim()).replaceFirst("").trim();
        stripped = LEADING_ARTICLE.matcher(stripped).replaceFirst("").trim();
        if (stripped.isEmpty()) {
            return List.of();
        }

        String[] words = stripped.split("\\s+");
        List<SplitItem> items = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        int i = 0;
        while (i < words.length) {
            if (i + 1 < words.length) {
                String twoWordWindow = (words[i] + " " + words[i + 1]).toLowerCase(Locale.ROOT);
                GenericItemCategory compoundCategory = COMPOUND_OVERRIDES.get(twoWordWindow);
                if (compoundCategory != null) {
                    items.add(emit(buffer, Arrays.asList(words[i], words[i + 1]), compoundCategory, activityContext));
                    buffer.clear();
                    i += 2;
                    continue;
                }
            }

            GenericItemCategory headCategory = classifySingleWord(words[i].toLowerCase(Locale.ROOT));
            if (headCategory != null) {
                items.add(emit(buffer, List.of(words[i]), headCategory, activityContext));
                buffer.clear();
                i++;
                continue;
            }

            // Not (yet) a recognized head noun - buffer it as a potential modifier
            // (color, material, activity term) for a later head noun in this segment.
            buffer.add(words[i]);
            i++;
        }
        // A leftover buffer with no head noun ever found is discarded - never invent a category.
        return items;
    }

    private static SplitItem emit(List<String> bufferWords, List<String> headWords, GenericItemCategory category, String activityContext) {
        List<String> allWords = new ArrayList<>(bufferWords);
        allWords.addAll(headWords);
        String phrase = String.join(" ", allWords);
        // Enhances a bare, otherwise-ambiguous generic noun (e.g. "shorts" on its own)
        // with the detected activity context (e.g. "soccer shorts"), but leaves an
        // already-descriptive phrase (e.g. "football boots") untouched.
        if (bufferWords.isEmpty()) {
            String lower = phrase.toLowerCase(Locale.ROOT);
            if (activityContext != null && BARE_GENERIC_TERMS.contains(lower)
                    && !lower.contains(activityContext.toLowerCase(Locale.ROOT))) {
                phrase = activityContext + " " + phrase;
            }
        }
        return new SplitItem(phrase, category);
    }

    /**
     * Returns {@code null} when the single word matches no recognized
     * garment/equipment keyword at all.
     */
    private static GenericItemCategory classifySingleWord(String lowerWord) {
        if (containsAny(lowerWord, ONE_PIECE_KEYWORDS)) {
            return GenericItemCategory.ONE_PIECE;
        }
        if (containsAny(lowerWord, FOOTWEAR_KEYWORDS)) {
            return GenericItemCategory.FOOTWEAR;
        }
        if (containsAny(lowerWord, BOTTOM_KEYWORDS)) {
            return GenericItemCategory.BOTTOM;
        }
        if (containsAny(lowerWord, OUTERWEAR_KEYWORDS)) {
            return GenericItemCategory.OUTERWEAR;
        }
        if (containsAny(lowerWord, TOP_KEYWORDS)) {
            return GenericItemCategory.TOP;
        }
        if (containsAny(lowerWord, ACCESSORY_KEYWORDS)) {
            return GenericItemCategory.ACCESSORY;
        }
        if (containsAny(lowerWord, EQUIPMENT_KEYWORDS)) {
            return GenericItemCategory.EQUIPMENT;
        }
        if (containsAny(lowerWord, OTHER_KEYWORDS)) {
            return GenericItemCategory.OTHER;
        }
        return null;
    }

    private static boolean containsAny(String lowerWord, List<String> keywords) {
        for (String keyword : keywords) {
            if (lowerWord.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
