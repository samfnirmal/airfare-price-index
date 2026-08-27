package com.whoisjson.airfarepriceindex;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class AirfareScraper {

    static class FlightQuote {
        String airline;
        String route;
        String bookingWindow; 
        int dayOffset;
        String travelDate;
        double price;

        public FlightQuote(String airline, String route, String bookingWindow, int dayOffset, String travelDate, double price) {
            this.airline = airline;
            this.route = route;
            this.bookingWindow = bookingWindow;
            this.dayOffset = dayOffset;
            this.travelDate = travelDate;
            this.price = price;
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=================================================");
        System.out.println("✈️  APIx: FAST-TRACK INDEX CALCULATOR (30s)");
        System.out.println("=================================================");
        
        System.out.print("Enter Target Airline (e.g., SpiceJet, IndiGo, Air India): ");
        String targetAirline = scanner.nextLine().trim();
        if (targetAirline.isEmpty()) {
            targetAirline = "SpiceJet"; 
        }
        
        // FAST TRACK: Only test 2 specific high-traffic routes
        String[][] targetRoutes = {
            {"DEL", "BOM"}, 
            {"BOM", "BLR"}
        };
        
        // FAST TRACK: Only test 3 strategic booking horizons (T+1, T+3, T+7)
        int[] targetDays = {1, 3, 7};
        
        DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("dd-MMM");

        List<FlightQuote> collectedQuotes = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            // FAST TRACK: Headless = true saves rendering time and memory
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .setViewportSize(1280, 720));
            
            System.out.println("\n🚀 Starting rapid data extraction for " + targetAirline.toUpperCase() + "...\n");
            Page page = context.newPage();

            for (String[] routePair : targetRoutes) {
                String origin = routePair[0];
                String dest = routePair[1];
                String route = origin + "-" + dest;
                
                System.out.print("🌐 Scraping Route: " + route + " ");

                for (int dayOffset : targetDays) {
                    LocalDate flightDate = LocalDate.now().plusDays(dayOffset);
                    String queryDateStr = flightDate.format(isoFormatter);
                    String displayDateStr = flightDate.format(displayFormatter);
                    String windowTag = "T+" + dayOffset;

                    try {
                        String googleUrl = String.format(
                            "https://www.google.com/travel/flights?q=Flights%%20from%%20%s%%20to%%20%s%%20on%%20%s",
                            origin, dest, queryDateStr
                        );
                        
                        page.navigate(googleUrl, new Page.NavigateOptions().setTimeout(25000));
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                        page.waitForTimeout(2000); // Shorter wait for headless mode

                        Locator listItems = page.locator("li");
                        int count = listItems.count();

                        for (int i = 0; i < count; i++) {
                            try {
                                String rawText = listItems.nth(i).innerText();
                                if (rawText != null && !rawText.isEmpty()) {
                                    String cleanText = rawText.replaceAll("\\n", " ");
                                    
                                    if (cleanText.toLowerCase().contains(targetAirline.toLowerCase()) && cleanText.contains("₹")) {
                                        String priceStr = cleanText.replaceAll(".*₹([0-9,]+).*", "$1").replace(",", "").trim();
                                        double price = Double.parseDouble(priceStr);

                                        collectedQuotes.add(new FlightQuote(
                                            targetAirline, route, windowTag, dayOffset, displayDateStr, price
                                        ));
                                    }
                                }
                            } catch (Exception ignored) { }
                        }
                        System.out.print("."); // Progress dot for each day checked

                    } catch (Exception e) {
                        System.out.print("x");
                    }
                }
                System.out.println(" Done.");
            }
            context.close();
            browser.close();
        }

        // COMPUTE & PRINT THE AIRFARE PRICE INDEX
        calculateAndPrintIndex(collectedQuotes, targetAirline);
        scanner.close();
    }

    private static void calculateAndPrintIndex(List<FlightQuote> quotes, String airline) {
        System.out.println("\n==================================================================================");
        System.out.println("📊 AIRFARE PRICE INDEX (API) COMPUTATION REPORT: " + airline.toUpperCase());
        System.out.println("==================================================================================");

        if (quotes.isEmpty()) {
            System.out.println("❌ No flight records collected to calculate index.");
            return;
        }

        double baselinePrice = 4500.0;

        Map<String, List<FlightQuote>> routeGroups = quotes.stream()
                .collect(Collectors.groupingBy(q -> q.route));

        System.out.printf("%-10s | %-12s | %-12s | %-10s | %-12s | %-10s\n", 
                "Route", "Min Fare", "Avg Fare", "Max Fare", "Quotes", "Route Index");
        System.out.println("----------------------------------------------------------------------------------");

        double totalNetworkIndex = 0.0;
        int routeCount = 0;

        for (Map.Entry<String, List<FlightQuote>> entry : routeGroups.entrySet()) {
            String route = entry.getKey();
            List<FlightQuote> list = entry.getValue();

            double minFare = list.stream().mapToDouble(q -> q.price).min().orElse(0.0);
            double avgFare = list.stream().mapToDouble(q -> q.price).average().orElse(0.0);
            double maxFare = list.stream().mapToDouble(q -> q.price).max().orElse(0.0);

            double routeIndex = (avgFare / baselinePrice) * 100.0;
            totalNetworkIndex += routeIndex;
            routeCount++;

            System.out.printf("%-10s | ₹%-11.0f | ₹%-11.0f | ₹%-9.0f | %-10d | %-10.2f\n",
                    route, minFare, avgFare, maxFare, list.size(), routeIndex);
        }

        double compositeNetworkIndex = routeCount > 0 ? (totalNetworkIndex / routeCount) : 0.0;

        System.out.println("----------------------------------------------------------------------------------");
        System.out.printf("🎯 COMPOSITE NETWORK AIRFARE INDEX (NAI): %.2f (Base = 100.00 @ ₹%.0f)\n", 
                compositeNetworkIndex, baselinePrice);
        System.out.println("==================================================================================\n");

        System.out.println("📈 BOOKING WINDOW PRICE SPREAD (DYNAMIC PRICING CURVE):");
        Map<String, List<FlightQuote>> windowGroups = quotes.stream()
                .collect(Collectors.groupingBy(q -> q.bookingWindow));

        // Sort tags nicely (T+1, T+3, T+7)
        List<String> sortedWindows = new ArrayList<>(windowGroups.keySet());
        Collections.sort(sortedWindows);

        for (String tag : sortedWindows) {
            List<FlightQuote> wList = windowGroups.get(tag);
            double wAvg = wList.stream().mapToDouble(q -> q.price).average().orElse(0.0);
            System.out.printf("   %-5s : Average Fare = ₹%-8.0f | Quotes Logged: %d\n", 
                    tag, wAvg, wList.size());
        }
        System.out.println("==================================================================================");
    }
}