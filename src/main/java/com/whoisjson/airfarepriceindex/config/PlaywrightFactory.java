package com.whoisjson.airfarepriceindex.config;

import com.microsoft.playwright.*;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.util.List;

@Component
public class PlaywrightFactory {

    private Playwright playwright;
    private Browser browser;

    public PlaywrightFactory() {
        this.playwright = Playwright.create();

        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(true) // Run headless
                .setArgs(List.of(
                        "--disable-blink-features=AutomationControlled",
                        "--disable-infobars",
                        "--window-position=0,0",
                        "--ignore-certificate-errors",
                        "--ignore-certificate-errors-spki-list",
                        "--disable-default-apps",
                        "--disable-extensions"));
        this.browser = playwright.chromium().launch(options);
    }

    /**
     * Creates a new stealth context with a standard User-Agent and stealth scripts.
     */
    public BrowserContext createStealthContext() {
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setUserAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080);

        BrowserContext context = browser.newContext(contextOptions);

        // Inject stealth scripts upon page initialization to counter basic bot
        // detection
        context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
        context.addInitScript("Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]})"); // Mock
                                                                                                            // plugins
        context.addInitScript("Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']})");

        // Ensure HeadlessChrome is masked
        context.addInitScript("const originalQuery = window.navigator.permissions.query;" +
                "window.navigator.permissions.query = (parameters) => (" +
                "parameters.name === 'notifications' ? Promise.resolve({ state: Notification.permission }) : originalQuery(parameters)"
                +
                ");");

        return context;
    }

    @PreDestroy
    public void cleanup() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
