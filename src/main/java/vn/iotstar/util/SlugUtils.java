package vn.iotstar.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class SlugUtils {

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern EDGESDHASHES = Pattern.compile("(^-|-$)");

    public static String toSlug(String input) {
        if (input == null) return "";

        String nowhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        // Normalize Vietnamese characters
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        // Replace đ and Đ specifically
        normalized = normalized.replaceAll("[đĐ]", "d");
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = EDGESDHASHES.matcher(slug).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}

