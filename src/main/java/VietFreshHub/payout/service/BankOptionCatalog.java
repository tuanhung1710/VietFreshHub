package VietFreshHub.payout.service;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BankOptionCatalog {

    private static final String API_URL = "https://api.vietqr.io";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration FAILURE_CACHE_TTL = Duration.ofHours(1);
    private static final Pattern BANK_CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> NON_BANK_CODES = Set.of(
            "MOMO", "ZALOPAY", "VTLMONEY", "VNPTMONEY", "MVAS"
    );
    private static final List<BankOption> FALLBACK_BANKS = List.of(
            new BankOption("ABB", "ABBANK"),
            new BankOption("ACB", "ACB"),
            new BankOption("BAB", "BacABank"),
            new BankOption("BIDV", "BIDV"),
            new BankOption("BVB", "BaoVietBank"),
            new BankOption("CBB", "CBBank"),
            new BankOption("CIMB", "CIMB"),
            new BankOption("COOPBANK", "COOPBANK"),
            new BankOption("EIB", "Eximbank"),
            new BankOption("HDB", "HDBank"),
            new BankOption("ICB", "VietinBank"),
            new BankOption("IVB", "IndovinaBank"),
            new BankOption("KLB", "KienLongBank"),
            new BankOption("KBANK", "KBank"),
            new BankOption("LPB", "LPBank"),
            new BankOption("MB", "MBBank"),
            new BankOption("MBV", "MBV"),
            new BankOption("MSB", "MSB"),
            new BankOption("NAB", "NamABank"),
            new BankOption("NCB", "NCB"),
            new BankOption("OCB", "OCB"),
            new BankOption("PGB", "PGBank"),
            new BankOption("PVCB", "PVcomBank"),
            new BankOption("SCB", "SCB"),
            new BankOption("SEAB", "SeABank"),
            new BankOption("SGICB", "SaigonBank"),
            new BankOption("SHB", "SHB"),
            new BankOption("SHBVN", "ShinhanBank"),
            new BankOption("STB", "Sacombank"),
            new BankOption("TCB", "Techcombank"),
            new BankOption("TPB", "TPBank"),
            new BankOption("VAB", "VietABank"),
            new BankOption("VBA", "Agribank"),
            new BankOption("VCCB", "VietCapitalBank"),
            new BankOption("VCB", "Vietcombank"),
            new BankOption("VIB", "VIB"),
            new BankOption("VIETBANK", "VietBank"),
            new BankOption("VPB", "VPBank"),
            new BankOption("WVN", "Woori")
    );

    private final RestClient restClient;
    private volatile CachedBankList cachedBanks;

    public BankOptionCatalog() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(4));
        this.restClient = RestClient.builder()
                .baseUrl(API_URL)
                .requestFactory(requestFactory)
                .build();
    }

    public List<BankOption> withSavedBank(String savedCode, String savedName) {
        List<BankOption> options = new ArrayList<>(getBanks());
        if (savedCode != null && !savedCode.isBlank() && findByCode(savedCode, options).isEmpty()) {
            String displayName = savedName == null || savedName.isBlank() ? savedCode : savedName;
            options.add(0, new BankOption(savedCode, displayName));
        }
        return List.copyOf(options);
    }

    public Optional<BankOption> findByCode(String code) {
        return findByCode(code, getBanks());
    }

    private List<BankOption> getBanks() {
        Instant now = Instant.now();
        CachedBankList current = cachedBanks;
        if (current != null && now.isBefore(current.expiresAt())) {
            return current.banks();
        }

        synchronized (this) {
            current = cachedBanks;
            now = Instant.now();
            if (current != null && now.isBefore(current.expiresAt())) {
                return current.banks();
            }

            try {
                List<BankOption> refreshed = fetchBanks();
                cachedBanks = new CachedBankList(refreshed, now.plus(CACHE_TTL));
                return refreshed;
            } catch (RestClientException | IllegalStateException exception) {
                List<BankOption> fallback = current == null ? FALLBACK_BANKS : current.banks();
                cachedBanks = new CachedBankList(fallback, now.plus(FAILURE_CACHE_TTL));
                return fallback;
            }
        }
    }

    private List<BankOption> fetchBanks() {
        VietQrBankResponse response = restClient.get()
                .uri("/v2/banks")
                .retrieve()
                .body(VietQrBankResponse.class);
        if (response == null || !"00".equals(response.code()) || response.data() == null) {
            throw new IllegalStateException("VietQR returned an invalid bank list.");
        }

        List<BankOption> banks = response.data().stream()
                .filter(bank -> bank.code() != null && bank.shortName() != null)
                .filter(bank -> BANK_CODE_PATTERN.matcher(bank.code()).matches())
                .filter(bank -> !NON_BANK_CODES.contains(bank.code().toUpperCase(Locale.ROOT)))
                .map(bank -> new BankOption(
                        bank.code().toUpperCase(Locale.ROOT),
                        bank.shortName().strip()
                ))
                .filter(bank -> !bank.name().isBlank())
                .distinct()
                .sorted(Comparator.comparing(BankOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (banks.isEmpty()) {
            throw new IllegalStateException("VietQR returned an empty bank list.");
        }
        return banks;
    }

    private Optional<BankOption> findByCode(String code, List<BankOption> banks) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return banks.stream()
                .filter(bank -> bank.code().equalsIgnoreCase(code.strip()))
                .findFirst();
    }

    private record CachedBankList(List<BankOption> banks, Instant expiresAt) {
    }

    private record VietQrBankResponse(String code, String desc, List<VietQrBank> data) {
    }

    private record VietQrBank(String code, String name, String shortName) {
    }

    public record BankOption(String code, String name) {
    }
}
