package eu.howarth.mcp.mediawiki.client;

public class MediaWikiException extends RuntimeException {
    public MediaWikiException(String message) {
        super(message);
    }

    public MediaWikiException(String message, Throwable cause) {
        super(message, cause);
    }
}
