package com.hope.trading.ctrader.openapi.protocol;

public final class CTraderPayloadTypes {
    public static final int APPLICATION_AUTH_REQUEST = 2100;
    public static final int APPLICATION_AUTH_RESPONSE = 2101;
    public static final int ACCOUNT_AUTH_REQUEST = 2102;
    public static final int ACCOUNT_AUTH_RESPONSE = 2103;
    public static final int ACCOUNT_LIST_REQUEST = 2149;
    public static final int ACCOUNT_LIST_RESPONSE = 2150;
    public static final int TRADER_REQUEST = 2121;
    public static final int TRADER_RESPONSE = 2122;
    public static final int RECONCILE_REQUEST = 2124;
    public static final int RECONCILE_RESPONSE = 2125;
    public static final int DEAL_LIST_REQUEST = 2133;
    public static final int DEAL_LIST_RESPONSE = 2134;
    public static final int ASSET_LIST_REQUEST = 2112;
    public static final int ASSET_LIST_RESPONSE = 2113;
    public static final int SYMBOL_LIST_REQUEST = 2114;
    public static final int SYMBOL_LIST_RESPONSE = 2115;
    public static final int POSITION_PNL_REQUEST = 2187;
    public static final int POSITION_PNL_RESPONSE = 2188;
    public static final int NEW_ORDER_REQUEST = 2106;
    public static final int EXECUTION_EVENT = 2126;
    public static final int SYMBOL_BY_ID_REQUEST = 2116;
    public static final int SYMBOL_BY_ID_RESPONSE = 2117;
    public static final int SUBSCRIBE_SPOTS_REQUEST = 2127;
    public static final int SUBSCRIBE_SPOTS_RESPONSE = 2128;
    public static final int UNSUBSCRIBE_SPOTS_REQUEST = 2129;
    public static final int UNSUBSCRIBE_SPOTS_RESPONSE = 2130;
    public static final int SPOT_EVENT = 2131;
    public static final int SUBSCRIBE_LIVE_TRENDBAR_REQUEST = 2135;
    public static final int UNSUBSCRIBE_LIVE_TRENDBAR_REQUEST = 2136;
    public static final int GET_TRENDBARS_REQUEST = 2137;
    public static final int GET_TRENDBARS_RESPONSE = 2138;
    public static final int SUBSCRIBE_LIVE_TRENDBAR_RESPONSE = 2165;
    public static final int UNSUBSCRIBE_LIVE_TRENDBAR_RESPONSE = 2166;

    private CTraderPayloadTypes() { }
}
