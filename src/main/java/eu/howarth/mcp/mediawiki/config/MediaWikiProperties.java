package eu.howarth.mcp.mediawiki.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "mediawiki")
public interface MediaWikiProperties {
    String url();
    String botUser();
    String botPassword();
}