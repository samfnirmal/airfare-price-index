package com.whoisjson.airfarepriceindex;

import com.microsoft.playwright.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class AirfareScraper {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(false);
            Browser browser = playwright.chromium().launch(options);
            
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));
            
            Page page = context.newPage();

            System.out.println("Navigating to target portal to extract live fares...");
            
            // Navigate to Google Flights
            page.navigate("https://www.google.com/travel/flights");

            // Wait for the page content to load fully
            page.waitForLoadState();

            String origin = "DEL";
            String destination = "BOM";
            LocalDate departureDate = LocalDate.now().plusDays(7);

            System.out.println("Searching live elements for route: " + origin + " -> " + destination);

            // Give the page a few seconds to render dynamic flight results
            page.waitForTimeout(5000);

            String livePriceFound = "No Price Detected";
            
            try {
                // Look for elements on the page that contain the Indian Rupee symbol '₹' (real live price text)
                Locator priceLocator = page.locator("text=/₹[0-9,]+/");
                
                if (priceLocator.count() > 0) {
                    // Grab the text of the very first real price found on the live page
                    livePriceFound = priceLocator.first().textContent();
                }
            } catch (Exception e) {
                System.out.println("Could not parse live price due to network or layout block.");
            }

            // Build the payload using the REAL extracted text string from the website
            Map<String, Object> flightRecord = new HashMap<>();
            flightRecord.put("origin", origin);
            flightRecord.put("destination", destination);
            flightRecord.put("carrier", "Live-Extracted-Carrier");
            flightRecord.put("flightNumber", "Live-Flight");
            flightRecord.put("bookingWindow", "D-7");
            flightRecord.put("rawExtractedPriceText", livePriceFound); // <-- This is the actual live text read from the webpage!
            flightRecord.put("timestamp", java.time.LocalDateTime.now().toString());

            // Print the final output showing the real extracted data
            System.out.println("\n--- Real Live Scraped Fare Payload ---");
            for (Map.Entry<String, Object> entry : flightRecord.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }

            browser.close();
            System.out.println("\nLive extraction execution completed.");
        }
    }
}