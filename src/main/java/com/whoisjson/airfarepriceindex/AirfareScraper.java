package com.whoisjson.airfarepriceindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Scanner;

public class AirfareScraper {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=================================================");
        System.out.println("✈️  APIx: KAFKA PRODUCER (STREAMING SCRAPER)");
        System.out.println("=================================================");
        
        System.out.print("Enter Target Airline (e.g., SpiceJet, IndiGo): ");
        String targetAirline = scanner.nextLine().trim();
        if (targetAirline.isEmpty()) { targetAirline = "SpiceJet"; }
        
        // Fast-Track Matrix
        String[][] targetRoutes = {{"DEL", "BOM"}, {"BOM", "BLR"}};
        int[] targetDays = {1, 3, 7};
        
        DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("dd-MMM");

        // 1. Initialize Kafka Producer
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        ObjectMapper mapper = new ObjectMapper();
        String topic = "raw-flight-fares";

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720));
            Page page = context.newPage();

            System.out.println("\n🚀 Streaming live quotes to Kafka Topic: '" + topic + "'...\n");

            for (String[] routePair : targetRoutes) {
                String origin = routePair[0];
                String dest = routePair[1];
                String route = origin + "-" + dest;

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
                        page.waitForTimeout(2000);

                        Locator listItems = page.locator("li");
                        for (int i = 0; i < listItems.count(); i++) {
                            try {
                                String cleanText = listItems.nth(i).innerText().replaceAll("\\n", " ");
                                
                                if (cleanText.toLowerCase().contains(targetAirline.toLowerCase()) && cleanText.contains("₹")) {
                                    String priceStr = cleanText.replaceAll(".*₹([0-9,]+).*", "$1").replace(",", "").trim();
                                    double price = Double.parseDouble(priceStr);

                                    // Create Event & Convert to JSON
                                    FlightEvent event = new FlightEvent(targetAirline, route, windowTag, dayOffset, displayDateStr, price);
                                    String jsonPayload = mapper.writeValueAsString(event);
                                    
                                    // 2. Publish to Kafka
                                    ProducerRecord<String, String> record = new ProducerRecord<>(topic, route, jsonPayload);
                                    producer.send(record);
                                    
                                    System.out.println(" 📤 [PRODUCED] " + route + " | " + windowTag + " | ₹" + price);
                                }
                            } catch (Exception ignored) { }
                        }
                    } catch (Exception e) {
                        System.out.println(" ❌ Error on " + route);
                    }
                }
            }
            context.close();
            browser.close();
        } finally {
            producer.flush();
            producer.close();
            scanner.close();
            System.out.println("\n✅ Scraping session complete. All events pushed to Kafka.");
        }
    }
}