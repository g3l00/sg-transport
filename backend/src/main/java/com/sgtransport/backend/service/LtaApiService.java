package com.sgtransport.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtransport.backend.model.BusStop;
import com.sgtransport.backend.repository.BusStopRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class LtaApiService {

    private static final Logger logger = LoggerFactory.getLogger(LtaApiService.class);
    private static final int LTA_PAGE_SIZE = 500;
    private static final int MAX_BUS_STOP_RECORDS = 20_000;

    @Value("${lta.api.key}")
    private String ltaApiKey;

    @Value("${lta.api.url}")
    private String ltaApiUrl;

    @Value("${lta.mock.enabled:false}")
    private boolean mockDataEnabled;

    @Autowired
    private WebClient webClient;

    @Autowired
    private BusStopRepository busStopRepository;

    @Autowired
    private MockDataService mockDataService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Fetch and cache bus stops from LTA API
     */
    @Cacheable("busStops")
    public String getBusStops() {
        try {
            logger.info("Fetching bus stops from LTA API");
            String response = fetchAllBusStops();
            logger.info("Successfully fetched bus stops from LTA API");
            return response;
        } catch (Exception e) {
            return fallbackBusStops(e);
        }
    }

    /**
     * Get real-time bus arrival
     */
    @Cacheable(value = "busArrival", key = "#busStopCode")
    public String getBusArrival(String busStopCode) {
        try {
            validateApiKey();
            logger.info("Fetching bus arrival for: {}", busStopCode);
            String busArrivalUrl = ltaApiUrl
                    + "/v3/BusArrival?BusStopCode="
                    + URLEncoder.encode(busStopCode, StandardCharsets.UTF_8);

            String response = webClient.get()
                    .uri(busArrivalUrl)
                    .header("AccountKey", ltaApiKey)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            logger.info("Successfully fetched bus arrival for: {}", busStopCode);
            return response;
        } catch (Exception e) {
            return fallbackBusArrival(busStopCode, e);
        }
    }

    /**
     * Search bus stop by name
     */
    public List<BusStop> searchBusStop(String name) {
        String query = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);

        if (query.isBlank()) {
            return List.of();
        }

        try {
            JsonNode busStops = objectMapper.readTree(getBusStops()).path("value");
            List<BusStop> results = new ArrayList<>();

            if (busStops.isArray()) {
                for (JsonNode busStopNode : busStops) {
                    BusStop busStop = toBusStop(busStopNode);
                    String searchableText = String.join(" ",
                            busStop.getCode(),
                            busStop.getName(),
                            busStop.getRoadName()
                    ).toLowerCase(Locale.ROOT);

                    if (searchableText.contains(query)) {
                        results.add(busStop);
                    }

                    if (results.size() >= 20) {
                        break;
                    }
                }
            }

            return results;
        } catch (Exception e) {
            logger.warn("Failed to search LTA bus stops, falling back to repository: {}", e.getMessage());
            return busStopRepository.findByNameContainingIgnoreCase(name);
        }
    }

    private String fetchAllBusStops() throws Exception {
        validateApiKey();

        ArrayNode allBusStops = objectMapper.createArrayNode();
        int skip = 0;

        while (skip < MAX_BUS_STOP_RECORDS) {
            String busStopsUrl = ltaApiUrl + "/BusStops?$skip=" + skip;

            String response = webClient.get()
                    .uri(busStopsUrl)
                    .header("AccountKey", ltaApiKey)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode value = objectMapper.readTree(response).path("value");

            if (!value.isArray() || value.isEmpty()) {
                break;
            }

            allBusStops.addAll((ArrayNode) value);

            if (value.size() < LTA_PAGE_SIZE) {
                break;
            }

            skip += LTA_PAGE_SIZE;
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("odata.metadata", ltaApiUrl + "/$metadata#BusStops");
        root.set("value", allBusStops);

        return objectMapper.writeValueAsString(root);
    }

    private BusStop toBusStop(JsonNode busStopNode) {
        return new BusStop(
                busStopNode.path("BusStopCode").asText(),
                busStopNode.path("Description").asText(),
                busStopNode.path("Latitude").asDouble(),
                busStopNode.path("Longitude").asDouble(),
                busStopNode.path("RoadName").asText()
        );
    }

    private void validateApiKey() {
        if (!StringUtils.hasText(ltaApiKey)) {
            throw new IllegalStateException("LTA_API_KEY is required for production LTA data");
        }
    }

    private String fallbackBusStops(Exception e) {
        if (mockDataEnabled) {
            logger.warn("Failed to fetch bus stops from LTA API, using mock data: {}", e.getMessage());
            return mockDataService.getMockBusStops();
        }

        throw new IllegalStateException("Failed to fetch bus stops from LTA API", e);
    }

    private String fallbackBusArrival(String busStopCode, Exception e) {
        if (mockDataEnabled) {
            logger.warn("Failed to fetch bus arrival from LTA API, using mock data: {}", e.getMessage());
            return mockDataService.getMockBusArrival(busStopCode);
        }

        throw new IllegalStateException("Failed to fetch bus arrival from LTA API", e);
    }
}
