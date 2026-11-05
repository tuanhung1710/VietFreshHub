package VietFreshHub.common;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.format.Formatter;
import org.springframework.stereotype.Component;

@Component("vndAmounts")
public class VndAmountFormatter implements Formatter<BigDecimal> {
    private static final Pattern AMOUNT = Pattern.compile(
            "[+-]?(?:(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?|\\.[0-9]+)");

    @Override
    public BigDecimal parse(String text, Locale locale) throws ParseException {
        String value = text.strip();
        if (!AMOUNT.matcher(value).matches()) {
            throw new ParseException("Số tiền không hợp lệ.", 0);
        }
        return new BigDecimal(value.replace(",", ""));
    }

    @Override
    public String print(BigDecimal amount, Locale locale) {
        return format(amount);
    }

    public String format(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        var format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        format.setMaximumFractionDigits(Math.max(0, amount.stripTrailingZeros().scale()));
        return format.format(amount);
    }
}
