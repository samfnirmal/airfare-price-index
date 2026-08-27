package com.whoisjson.airfarepriceindex.controller;

import com.whoisjson.airfarepriceindex.service.FlightScraperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scraping")
public class ScrapingController {

    private final FlightScraperService scraperService;

    public ScrapingController(FlightScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @PostMapping("/trigger")
    public ResponseEntity<String> triggerScrape(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam String date) {

        // We trigger this asynchronously in a real app, but for scaffold testing it can
        // block or be spun into a virtual thread.
        Thread.startVirtualThread(() -> {
            scraperService.scrapeFlightPrices(origin, destination, date);
        });

        return ResponseEntity.accepted()
                .body("Scraping job queued for " + origin + " to " + destination + " on " + date);
    }
}
