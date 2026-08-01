package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.mapper;

import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerProtocolException;
import java.util.*;

public final class KrakenAssetNormalizer {
    private static final Map<String,String> ASSETS=assets();

    private KrakenAssetNormalizer() {}

    public static String asset(String providerAsset) {
        String normalized=providerAsset==null?null:ASSETS.get(providerAsset.trim().toUpperCase(Locale.ROOT));
        if(normalized==null)throw new BrokerProtocolException("Unsupported Kraken asset alias");
        return normalized;
    }

    public static Pair pair(String providerPair) {
        if(providerPair==null||providerPair.isBlank())throw new BrokerProtocolException("Missing Kraken pair");
        String value=providerPair.trim().toUpperCase(Locale.ROOT);
        if(value.contains("/")||value.contains("-")) {
            String[] parts=value.split("[/\\-]",-1);
            if(parts.length!=2)return unsupportedPair();
            return new Pair(asset(parts[0]),asset(parts[1]));
        }
        Set<Pair> candidates=new LinkedHashSet<>();
        for(int split=1;split<value.length();split++) {
            String base=ASSETS.get(value.substring(0,split));
            String quote=ASSETS.get(value.substring(split));
            if(base!=null&&quote!=null)candidates.add(new Pair(base,quote));
        }
        if(candidates.size()!=1)return unsupportedPair();
        return candidates.iterator().next();
    }

    private static Pair unsupportedPair() {
        throw new BrokerProtocolException("Unsupported or ambiguous Kraken pair");
    }

    private static Map<String,String> assets() {
        Map<String,String> values=new HashMap<>();
        aliases(values,"BTC","BTC","XBT","XXBT");
        aliases(values,"ETH","ETH","XETH");
        aliases(values,"DOGE","DOGE","XDG","XXDG");
        aliases(values,"LTC","LTC","XLTC");
        aliases(values,"XRP","XRP","XXRP");
        aliases(values,"XLM","XLM","XXLM");
        aliases(values,"XMR","XMR","XXMR");
        aliases(values,"ETC","ETC","XETC");
        aliases(values,"ZEC","ZEC","XZEC");
        aliases(values,"MLN","MLN","XMLN");
        aliases(values,"USD","USD","ZUSD");
        aliases(values,"EUR","EUR","ZEUR");
        aliases(values,"GBP","GBP","ZGBP");
        aliases(values,"JPY","JPY","ZJPY");
        aliases(values,"CAD","CAD","ZCAD");
        aliases(values,"AUD","AUD","ZAUD");
        aliases(values,"CHF","CHF","ZCHF");
        for(String canonical:List.of("AAVE","ADA","ALGO","ATOM","BCH","DAI","DOT","EOS",
                "ICP","LINK","MATIC","POL","SOL","TRX","UNI","USDC","USDT"))
            aliases(values,canonical,canonical);
        return Map.copyOf(values);
    }

    private static void aliases(Map<String,String> target,String canonical,String... aliases) {
        for(String alias:aliases)target.put(alias,canonical);
    }

    public record Pair(String baseAsset,String quoteAsset) {
        public String instrument() { return baseAsset+"/"+quoteAsset; }
    }
}
