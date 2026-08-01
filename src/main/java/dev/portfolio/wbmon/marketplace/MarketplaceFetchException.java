package dev.portfolio.wbmon.marketplace;

public class MarketplaceFetchException extends RuntimeException {

    public MarketplaceFetchException(String message) {
        super(message);
    }

    public MarketplaceFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
