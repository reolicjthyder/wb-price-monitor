package dev.portfolio.wbmon.marketplace;

/** Ответ получен, но его формат не тот, что мы умеем разбирать. Ретраить бессмысленно. */
public class UnexpectedResponseException extends MarketplaceFetchException {

    public UnexpectedResponseException(String message) {
        super(message);
    }
}
