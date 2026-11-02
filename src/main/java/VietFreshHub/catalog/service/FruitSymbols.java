package VietFreshHub.catalog.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Biểu tượng trình bày của template; dữ liệu sản phẩm và giá vẫn lấy từ database. */
public final class FruitSymbols {
    private record Symbol(String text, String emoji) { }
    private static final List<Symbol> SYMBOLS = List.of(
            new Symbol("nhap khau", "🍎"), new Symbol("viet nam", "🍊"), new Symbol("noi dia", "🍊"),
            new Symbol("nhiet doi", "🥭"), new Symbol("theo mua", "🍓"), new Symbol("cao cap", "🍒"),
            new Symbol("so che", "🍍"), new Symbol("cat san", "🍍"), new Symbol("qua tang", "🍒"),
            new Symbol("tao", "🍎"), new Symbol("cam", "🍊"), new Symbol("quyt", "🍊"), new Symbol("le", "🍐"),
            new Symbol("nho", "🍇"), new Symbol("chuoi", "🍌"), new Symbol("buoi", "🍈"),
            new Symbol("thanh long", "🐉"), new Symbol("dua hau", "🍉"), new Symbol("xoai", "🥭"),
            new Symbol("dua xi", "🥥"), new Symbol("dua mat", "🍍"), new Symbol("dua cat", "🍍"), new Symbol("dua", "🍍"),
            new Symbol("dau", "🍓"), new Symbol("man", "🍑"), new Symbol("vai", "🍒"),
            new Symbol("cherry", "🍒"), new Symbol("kiwi", "🥝"), new Symbol("viet quat", "🫐"), new Symbol("combo", "🍓"));

    private FruitSymbols() { }

    public static String forName(String name) {
        String text = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return SYMBOLS.stream().filter(symbol -> text.contains(symbol.text())).findFirst().map(Symbol::emoji).orElse("🍎");
    }
}
