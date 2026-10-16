package VietFreshHub.common;

public final class PageSizes {
    public static final int DEFAULT = 15;
    private PageSizes() {}
    public static int normalize(int requested) {
        return requested == 20 || requested == 50 ? requested : DEFAULT;
    }
}
