package VietFreshHub.common;

import java.util.List;

public final class PageSizes {
    public static final int DEFAULT = 10;
    public static final List<Integer> OPTIONS = List.of(5, DEFAULT, 20, 50);

    private PageSizes() {}

    public static int normalize(int requested) {
        return isAllowed(requested) ? requested : DEFAULT;
    }

    public static boolean isAllowed(int requested) {
        return OPTIONS.contains(requested);
    }
}
