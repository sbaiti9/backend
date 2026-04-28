package tn.esprit.training.chat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntentDetector {
    public static class Result {
        public final Intent intent;
        public final String param;

        public Result(Intent intent, String param) {
            this.intent = intent;
            this.param = param;
        }
    }

    public static Result detect(String message) {
        if (message == null) return new Result(Intent.HELP, null);
        String m = message.toLowerCase(Locale.ROOT).trim();
        if (containsAny(m, "top rated", "top", "best", "meilleur", "rating", "note")) {
            return new Result(Intent.TOP_RATED, null);
        }
        if (containsAny(m, "most reviewed", "plus d'avis", "most avis", "popular", "populaire")) {
            return new Result(Intent.MOST_REVIEWED, null);
        }
        if (containsAny(m, "cheap", "cheapest", "moins cher", "price low", "low price")) {
            return new Result(Intent.CHEAPEST, null);
        }
        if (containsAny(m, "expensive", "plus cher", "most expensive")) {
            return new Result(Intent.MOST_EXPENSIVE, null);
        }
        if (containsAny(m, "no reviews", "sans avis", "0 avis", "no rating")) {
            return new Result(Intent.NO_REVIEWS, null);
        }
        if (containsAny(m, "reviews of", "avis de", "comments of", "feedback")) {
            String q = extractQuery(m, "(reviews of|avis de|comments of|feedback)\\s+(.*)");
            return new Result(Intent.REVIEWS_FOR_COURSE, q);
        }
        if (containsAny(m, "search", "find", "course", "training")) {
            String q = extractQuery(m, "(search|find|course|training)\\s+(.*)");
            return new Result(Intent.SEARCH_COURSE, q);
        }
        return new Result(Intent.HELP, null);
    }

    private static boolean containsAny(String m, String... keys) {
        for (String k : keys) if (m.contains(k)) return true;
        return false;
    }

    private static String extractQuery(String m, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher matcher = p.matcher(m);
        if (matcher.find()) {
            String g = matcher.group(matcher.groupCount());
            return g != null ? g.trim() : null;
        }
        return null;
    }
}

