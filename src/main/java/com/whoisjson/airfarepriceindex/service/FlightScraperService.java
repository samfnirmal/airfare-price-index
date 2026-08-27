package com.whoisjson.airfarepriceindex.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.time.Duration;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.StreamSupport;

@Service
public class FlightScraperService {

    private static final Logger log = LoggerFactory.getLogger(FlightScraperService.class);

    private final CookieManagerService cookieManagerService;
    private final WebClient webClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final Bucket bucket;
    private final Random random = new Random();

    private static final String EASEMYTRIP_API_ENDPOINT = "https://flight.easemytrip.com/E_FlightSearch/MultiPaging";
    private static final String KAFKA_TOPIC = "airfare-raw-stream";
    private static final String PROVIDER_NAME = "easemytrip";

    // Allowed target airlines
    private static final Set<String> ALLOWED_AIRLINES = Set.of(
            "indigo", "air india", "air india express", "spicejet");

    public FlightScraperService(CookieManagerService cookieManagerService,
            WebClient.Builder webClientBuilder,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.cookieManagerService = cookieManagerService;
        this.webClient = webClientBuilder.build();
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;

        // Rate limit: 15 requests per minute
        // This is a global rate limiter for the scaffold. In production, this can be
        // tracked per IP or proxy ID.
        Refill refill = Refill.intervally(15, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(15, refill);
        this.bucket = Bucket.builder().addLimit(limit).build();
    }

    /**
     * Executes the scraper for a given route and date.
     * 
     * @param origin      The origin airport code (e.g. DEL)
     * @param destination The destination airport code (e.g. BOM)
     * @param date        The date of travel (e.g. 2026-09-01)
     */
    public void scrapeFlightPrices(String origin, String destination, String date) {
        log.info("Requested scraping job for {} -> {} on {}", origin, destination, date);

        // 1. Rate Limiting Check
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded! Dropping request for {} -> {}", origin, destination);
            return;
        }

        // 2. Randomized Jitter Delay (1000ms - 3500ms)
        applyJitter(1000, 3500);

        // 3. Obtain Cookies
        String cookies = cookieManagerService.getCookies(PROVIDER_NAME);
        log.debug("Injecting cookies: {}", cookies);

        // 4. Execute API Request using WebClient
        log.info("Executing API request to extract fares...");

        // Format date from YYYY-MM-DD to DD/MM/YYYY if necessary.
        String formattedDate = date;
        if (date.contains("-")) {
            String[] parts = date.split("-");
            if (parts.length == 3) {
                formattedDate = parts[2] + "/" + parts[1] + "/" + parts[0];
            }
        }

        String requestBody = String.format(
                "{\"src\":\"%s\",\"des\":\"%s\",\"depDate\":\"%s\",\"rtDate\":\"\",\"Adults\":1,\"Children\":0,\"Infants\":0,\"CabinClass\":\"0\",\"IsTestFlight\":false}",
                origin, destination, formattedDate);

        // Blocking here for the scaffold, usually we handle reactively
        try {
            ResponseEntity<String> response = webClient.post()
                    .uri(EASEMYTRIP_API_ENDPOINT)
                    .header("Cookie", cookies)
                    .header("Content-Type", "application/json")
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Referer", "https://flight.easemytrip.com/")
                    .header("Origin", "https://flight.easemytrip.com")
                    .header("Accept", "application/json, text/plain, */*")
                    .bodyValue(requestBody)
                    .retrieve()
                    .toEntity(String.class)
                    .block();

            String jsonPayload = response.getBody();
            log.info("API Response Status: {}", response.getStatusCode());
            if (jsonPayload != null && !jsonPayload.isEmpty()) {
                String preview = jsonPayload.length() > 300 ? jsonPayload.substring(0, 300) + "..." : jsonPayload;
                log.info("API Response Payload preview: {}", preview);

                // Intercept and Filter
                String filteredPayload = filterAndDeduplicateFlights(jsonPayload);
                if (filteredPayload != null && !filteredPayload.equals("[]")) {
                    // 5. Normalize payload & push to Kafka
                    pushToKafka(origin, destination, date, filteredPayload);
                } else {
                    log.warn("No matchable flights found after filtering for {} -> {}", origin, destination);
                }
            } else {
                log.warn("Empty payload received.");
            }

        } catch (Exception e) {
            log.error("Error during flight data extraction: {}", e.getMessage());
        }
    }

    private String filterAndDeduplicateFlights(String rawJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(rawJson);

            // Try to locate array of flights iteratively (Fallback to Segment)
            JsonNode flightArray = rootNode.findPath("Flight");
            if (flightArray.isMissingNode() || !flightArray.isArray()) {
                flightArray = rootNode.findPath("Segment");
            }
            if (flightArray.isMissingNode() || !flightArray.isArray()) {
                log.warn("Could not find suitable Flight or Segment JSON Array.");
                return null;
            }

            ArrayNode filteredArray = objectMapper.createArrayNode();
            Set<String> seenFlightIds = new HashSet<>();

            StreamSupport.stream(flightArray.spliterator(), false)
                    .filter(node -> {
                        // Match airline name dynamically by scanning common schema nodes
                        String name = node.findPath("AirLineName").asText("").trim().toLowerCase();
                        if (name.isEmpty())
                            name = node.findPath("AirlineName").asText("").trim().toLowerCase();
                        if (name.isEmpty())
                            name = node.findPath("Airline").asText("").trim().toLowerCase();

                        final String finalName = name;
                        return ALLOWED_AIRLINES.stream().anyMatch(finalName::contains);
                    })
                    .filter(node -> {
                        // Deduplicate based on dynamic code and flight number
                        String airlineCode = node.findPath("AirLineCode").asText("").trim();
                        if (airlineCode.isEmpty())
                            airlineCode = node.findPath("AirlineCode").asText("").trim();

                        String flightNumber = node.findPath("FlightNumber").asText("").trim();
                        if (flightNumber.isEmpty())
                            flightNumber = node.findPath("FltNo").asText("").trim();

                        String uniqueId = airlineCode + "-" + flightNumber;
                        return !uniqueId.equals("-") && seenFlightIds.add(uniqueId);
                    })
                    .forEach(filteredArray::add);

            return objectMapper.writeValueAsString(filteredArray);
        } catch (Exception e) {
            log.error("JSON filtering error: {}", e.getMessage());
            return null;
        }
    }

    private void applyJitter(int minMs, int maxMs) {
        int delay = random.nextInt((maxMs - minMs) + 1) + minMs;
        log.info("Applying Jitter Delay of {} ms to mimic human request interval.", delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pushToKafka(String origin, String destination, String date, String payload) {
        // Here we could transform/normalize the JSON to a standardized model if needed.
        // For the scaffold, we just push the raw/mock JSON payload.
        String key = origin + "-" + destination + "-" + date;

        log.info("Pushing data to Kafka topic {} with key {}", KAFKA_TOPIC, key);
        kafkaTemplate.send(KAFKA_TOPIC, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully published fare data to Kafka topic: {}", KAFKA_TOPIC);
                    } else {
                        log.error("Failed to publish fare data to Kafka topic: {}", KAFKA_TOPIC, ex);
                    }
                });
    }
}
