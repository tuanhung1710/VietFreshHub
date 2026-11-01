package VietFreshHub.common;

import java.util.List;

public final class PageSizes {
    public static final int DEFAULT = 10;
    public static final List<Integer> OPTIONS = List.of(DEFAULT, 20, 50);

    private PageSizes() {}

    public static int normalize(int requested) {
        return OPTIONS.contains(requested) ? requested : DEFAULT;
    }
}
