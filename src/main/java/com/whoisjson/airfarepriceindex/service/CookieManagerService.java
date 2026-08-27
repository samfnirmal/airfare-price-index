package com.whoisjson.airfarepriceindex.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.Cookie;
import com.whoisjson.airfarepriceindex.config.PlaywrightFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CookieManagerService {

    private static final Logger log = LoggerFactory.getLogger(CookieManagerService.class);

    private final PlaywrightFactory playwrightFactory;

    // Simple cache to store cookies. For this scaffold we just use a map.
    // In production, this can be backed by Redis.
    // Key: providerName (e.g. "airline-a"), Value: cookieString
    private final ConcurrentHashMap<String, String> cookieCache = new ConcurrentHashMap<>();

    // The target homepage to clear anti-bot protection.
    // In a real scenario, this would be an array of airline URLs or
    // parameterizable.
    private static final String TARGET_AIRLINE_URL = "https://www.easemytrip.com/";

    public CookieManagerService(PlaywrightFactory playwrightFactory) {
        this.playwrightFactory = playwrightFactory;
    }

    /**
     * Optional initialization to warm up a session upon startup.
     */
    @PostConstruct
    public void init() {
        log.info("CookieManagerService initialized. Ready to warm sessions.");
        // We can do an initial warm up if needed here.
    }

    /**
     * Warms up a session by visiting a target page and extracting anti-bot cleared
     * cookies.
     * 
     * @param provider The name of the provider to warm (e.g. "airline-a")
     */
    public void warmSession(String provider) {
        log.info("Warming session for provider: {}", provider);

        try (BrowserContext context = playwrightFactory.createStealthContext()) {
            Page page = context.newPage();

            // Navigate to the target page to trigger anti-bot challenges
            log.info("Navigating to target URL: {}", TARGET_AIRLINE_URL);
            page.navigate(TARGET_AIRLINE_URL,
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(15000));

            // Wait until network activity settles to ensure cookies are populated
            page.waitForTimeout(3000);

            // Extract cookies
            List<Cookie> cookies = context.cookies();
            String cookieString = cookies.stream()
                    .map(c -> c.name + "=" + c.value)
                    .collect(Collectors.joining("; "));

            log.info("Successfully extracted {} cookies for provider {}", cookies.size(), provider);
            log.debug("Cookie string: {}", cookieString);

            // Store locally in cache
            cookieCache.put(provider, cookieString);

            page.close();
        } catch (Exception e) {
            log.error("Failed to warm session for provider {}: {}", provider, e.getMessage());
        }
    }

    /**
     * Retrieves the cached cookies for a given provider.
     * 
     * @param provider The name of the provider.
     * @return Cookies formatted as a header string (e.g. "key=val; key2=val2")
     */
    public String getCookies(String provider) {
        String cookies = cookieCache.get(provider);
        if (cookies == null || cookies.isEmpty()) {
            log.warn("No cached cookies found for provider {}. Initiating synchronous warmup...", provider);
            warmSession(provider);
            return cookieCache.get(provider);
        }
        return cookies;
    }
}
