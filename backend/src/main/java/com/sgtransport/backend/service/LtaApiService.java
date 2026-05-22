package com.sgtransport.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtransport.backend.model.BusStop;
import com.sgtransport.backend.model.RoutePlanResponse;
import com.sgtransport.backend.model.RoutePlanResponse.RouteLeg;
import com.sgtransport.backend.model.RoutePlanResponse.RouteOption;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LtaApiService {

    private static final Logger logger = LoggerFactory.getLogger(LtaApiService.class);
    private static final int LTA_PAGE_SIZE = 500;
    private static final int MAX_BUS_STOP_RECORDS = 20_000;
    private static final int MAX_BUS_ROUTE_RECORDS = 100_000;
    private static final int MAX_DIRECT_OPTIONS = 8;
    private static final int MAX_TRANSFER_OPTIONS = 8;

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
     * Fetch and cache detailed route records from LTA API
     */
    @Cacheable("busRoutes")
    public String getBusRoutes() {
        try {
            logger.info("Fetching bus routes from LTA API");
            String response = fetchAllBusRoutes();
            logger.info("Successfully fetched bus routes from LTA API");
            return response;
        } catch (Exception e) {
            return fallbackBusRoutes(e);
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

    public RoutePlanResponse planRoute(String fromCode, String toCode) {
        String normalizedFrom = normalizeStopCode(fromCode);
        String normalizedTo = normalizeStopCode(toCode);

        if (normalizedFrom.isBlank() || normalizedTo.isBlank()) {
            return new RoutePlanResponse(
                    unknownBusStop(normalizedFrom),
                    unknownBusStop(normalizedTo),
                    List.of()
            );
        }

        try {
            Map<String, BusStop> busStops = getBusStopLookup();
            BusStop fromStop = busStops.getOrDefault(normalizedFrom, unknownBusStop(normalizedFrom));
            BusStop toStop = busStops.getOrDefault(normalizedTo, unknownBusStop(normalizedTo));

            if (normalizedFrom.equals(normalizedTo)) {
                return new RoutePlanResponse(fromStop, toStop, List.of());
            }

            Map<RouteGroupKey, List<BusRouteStop>> routeGroups = getRouteGroups();
            List<RouteOption> directOptions = findDirectOptions(routeGroups, busStops, normalizedFrom, normalizedTo);
            List<RouteOption> transferOptions = findTransferOptions(routeGroups, busStops, normalizedFrom, normalizedTo);

            List<RouteOption> options = new ArrayList<>();
            options.addAll(directOptions.stream().limit(MAX_DIRECT_OPTIONS).toList());
            options.addAll(transferOptions.stream().limit(MAX_TRANSFER_OPTIONS).toList());
            options.sort(this::compareRouteOptions);

            return new RoutePlanResponse(fromStop, toStop, options.stream().limit(12).toList());
        } catch (Exception e) {
            logger.warn("Failed to plan route from {} to {}: {}", normalizedFrom, normalizedTo, e.getMessage());
            throw new IllegalStateException("Failed to plan route", e);
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

    private String fetchAllBusRoutes() throws Exception {
        validateApiKey();

        ArrayNode allBusRoutes = objectMapper.createArrayNode();
        int skip = 0;

        while (skip < MAX_BUS_ROUTE_RECORDS) {
            String busRoutesUrl = ltaApiUrl + "/BusRoutes?$skip=" + skip;

            String response = webClient.get()
                    .uri(busRoutesUrl)
                    .header("AccountKey", ltaApiKey)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode value = objectMapper.readTree(response).path("value");

            if (!value.isArray() || value.isEmpty()) {
                break;
            }

            allBusRoutes.addAll((ArrayNode) value);

            if (value.size() < LTA_PAGE_SIZE) {
                break;
            }

            skip += LTA_PAGE_SIZE;
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("odata.metadata", ltaApiUrl + "/$metadata#BusRoutes");
        root.set("value", allBusRoutes);

        return objectMapper.writeValueAsString(root);
    }

    private Map<String, BusStop> getBusStopLookup() throws Exception {
        JsonNode busStopNodes = objectMapper.readTree(getBusStops()).path("value");
        Map<String, BusStop> busStops = new HashMap<>();

        if (busStopNodes.isArray()) {
            for (JsonNode busStopNode : busStopNodes) {
                BusStop busStop = toBusStop(busStopNode);
                busStops.put(busStop.getCode(), busStop);
            }
        }

        return busStops;
    }

    private Map<RouteGroupKey, List<BusRouteStop>> getRouteGroups() throws Exception {
        JsonNode routeNodes = objectMapper.readTree(getBusRoutes()).path("value");
        Map<RouteGroupKey, List<BusRouteStop>> routeGroups = new HashMap<>();

        if (routeNodes.isArray()) {
            for (JsonNode routeNode : routeNodes) {
                BusRouteStop routeStop = toBusRouteStop(routeNode);
                RouteGroupKey key = new RouteGroupKey(routeStop.serviceNo(), routeStop.direction());
                routeGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(routeStop);
            }
        }

        routeGroups.values().forEach(stops ->
                stops.sort(Comparator.comparingInt(BusRouteStop::stopSequence))
        );

        return routeGroups;
    }

    private List<RouteOption> findDirectOptions(
            Map<RouteGroupKey, List<BusRouteStop>> routeGroups,
            Map<String, BusStop> busStops,
            String fromCode,
            String toCode
    ) {
        Map<String, RouteOption> bestOptions = new LinkedHashMap<>();

        for (List<BusRouteStop> routeStops : routeGroups.values()) {
            for (RouteLeg leg : findLegs(routeStops, busStops, fromCode, toCode)) {
                RouteOption option = routeOption("direct", null, List.of(leg));
                String key = leg.serviceNo() + ":" + leg.direction();
                bestOptions.merge(key, option, this::shorterRouteOption);
            }
        }

        return bestOptions.values().stream()
                .sorted(this::compareRouteOptions)
                .toList();
    }

    private List<RouteOption> findTransferOptions(
            Map<RouteGroupKey, List<BusRouteStop>> routeGroups,
            Map<String, BusStop> busStops,
            String fromCode,
            String toCode
    ) {
        List<RouteLeg> firstLegs = new ArrayList<>();
        Map<String, List<RouteLeg>> secondLegsByTransferStop = new HashMap<>();

        for (List<BusRouteStop> routeStops : routeGroups.values()) {
            collectFirstLegs(routeStops, busStops, fromCode, toCode, firstLegs);
            collectSecondLegs(routeStops, busStops, fromCode, toCode, secondLegsByTransferStop);
        }

        Map<String, RouteOption> bestOptions = new LinkedHashMap<>();

        for (RouteLeg firstLeg : firstLegs) {
            List<RouteLeg> matchingSecondLegs = secondLegsByTransferStop.get(firstLeg.to().getCode());

            if (matchingSecondLegs == null) {
                continue;
            }

            for (RouteLeg secondLeg : matchingSecondLegs) {
                if (sameServiceDirection(firstLeg, secondLeg)) {
                    continue;
                }

                RouteOption option = routeOption("transfer", firstLeg.to(), List.of(firstLeg, secondLeg));
                String key = firstLeg.serviceNo()
                        + ":" + firstLeg.direction()
                        + ">" + firstLeg.to().getCode()
                        + ">" + secondLeg.serviceNo()
                        + ":" + secondLeg.direction();
                bestOptions.merge(key, option, this::shorterRouteOption);
            }
        }

        return bestOptions.values().stream()
                .sorted(this::compareRouteOptions)
                .toList();
    }

    private void collectFirstLegs(
            List<BusRouteStop> routeStops,
            Map<String, BusStop> busStops,
            String fromCode,
            String toCode,
            List<RouteLeg> firstLegs
    ) {
        for (int i = 0; i < routeStops.size(); i++) {
            BusRouteStop start = routeStops.get(i);

            if (!start.busStopCode().equals(fromCode)) {
                continue;
            }

            for (int j = i + 1; j < routeStops.size(); j++) {
                BusRouteStop transfer = routeStops.get(j);

                if (isTransferCandidate(transfer.busStopCode(), fromCode, toCode)) {
                    firstLegs.add(toRouteLeg(start, transfer, busStops));
                }
            }
        }
    }

    private void collectSecondLegs(
            List<BusRouteStop> routeStops,
            Map<String, BusStop> busStops,
            String fromCode,
            String toCode,
            Map<String, List<RouteLeg>> secondLegsByTransferStop
    ) {
        for (int j = 0; j < routeStops.size(); j++) {
            BusRouteStop destination = routeStops.get(j);

            if (!destination.busStopCode().equals(toCode)) {
                continue;
            }

            for (int i = 0; i < j; i++) {
                BusRouteStop transfer = routeStops.get(i);

                if (isTransferCandidate(transfer.busStopCode(), fromCode, toCode)) {
                    RouteLeg leg = toRouteLeg(transfer, destination, busStops);
                    secondLegsByTransferStop
                            .computeIfAbsent(transfer.busStopCode(), ignored -> new ArrayList<>())
                            .add(leg);
                }
            }
        }
    }

    private List<RouteLeg> findLegs(
            List<BusRouteStop> routeStops,
            Map<String, BusStop> busStops,
            String fromCode,
            String toCode
    ) {
        List<RouteLeg> legs = new ArrayList<>();

        for (int i = 0; i < routeStops.size(); i++) {
            BusRouteStop start = routeStops.get(i);

            if (!start.busStopCode().equals(fromCode)) {
                continue;
            }

            for (int j = i + 1; j < routeStops.size(); j++) {
                BusRouteStop destination = routeStops.get(j);

                if (destination.busStopCode().equals(toCode)) {
                    legs.add(toRouteLeg(start, destination, busStops));
                }
            }
        }

        return legs;
    }

    private RouteLeg toRouteLeg(
            BusRouteStop from,
            BusRouteStop to,
            Map<String, BusStop> busStops
    ) {
        double distanceKm = Math.max(0, to.distance() - from.distance());
        int stops = Math.max(0, to.stopSequence() - from.stopSequence());

        return new RouteLeg(
                from.serviceNo(),
                from.operator(),
                from.direction(),
                busStops.getOrDefault(from.busStopCode(), unknownBusStop(from.busStopCode())),
                busStops.getOrDefault(to.busStopCode(), unknownBusStop(to.busStopCode())),
                from.stopSequence(),
                to.stopSequence(),
                roundDistance(distanceKm),
                stops
        );
    }

    private RouteOption routeOption(String type, BusStop transferStop, List<RouteLeg> legs) {
        double totalDistanceKm = legs.stream()
                .mapToDouble(RouteLeg::distanceKm)
                .sum();
        int totalStops = legs.stream()
                .mapToInt(RouteLeg::stops)
                .sum();

        return new RouteOption(type, transferStop, legs, roundDistance(totalDistanceKm), totalStops);
    }

    private RouteOption shorterRouteOption(RouteOption current, RouteOption candidate) {
        return compareRouteOptions(candidate, current) < 0 ? candidate : current;
    }

    private int compareRouteOptions(RouteOption left, RouteOption right) {
        return Comparator
                .comparingInt((RouteOption option) -> option.legs().size())
                .thenComparingInt(RouteOption::totalStops)
                .thenComparingDouble(RouteOption::totalDistanceKm)
                .thenComparing(option -> option.legs().get(0).serviceNo())
                .compare(left, right);
    }

    private boolean sameServiceDirection(RouteLeg firstLeg, RouteLeg secondLeg) {
        return firstLeg.serviceNo().equals(secondLeg.serviceNo())
                && firstLeg.direction() == secondLeg.direction();
    }

    private boolean isTransferCandidate(String busStopCode, String fromCode, String toCode) {
        return !busStopCode.equals(fromCode) && !busStopCode.equals(toCode);
    }

    private BusRouteStop toBusRouteStop(JsonNode routeNode) {
        return new BusRouteStop(
                routeNode.path("ServiceNo").asText(),
                routeNode.path("Operator").asText(),
                routeNode.path("Direction").asInt(),
                routeNode.path("StopSequence").asInt(),
                routeNode.path("BusStopCode").asText(),
                routeNode.path("Distance").asDouble()
        );
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

    private String normalizeStopCode(String busStopCode) {
        return busStopCode == null ? "" : busStopCode.trim();
    }

    private BusStop unknownBusStop(String busStopCode) {
        String code = normalizeStopCode(busStopCode);
        String label = code.isBlank() ? "Unknown stop" : "Bus stop " + code;
        return new BusStop(code, label, null, null, "Unknown road");
    }

    private double roundDistance(double value) {
        return Math.round(value * 10.0) / 10.0;
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

    private String fallbackBusRoutes(Exception e) {
        if (mockDataEnabled) {
            logger.warn("Failed to fetch bus routes from LTA API, using mock data: {}", e.getMessage());
            return mockDataService.getMockBusRoutes();
        }

        throw new IllegalStateException("Failed to fetch bus routes from LTA API", e);
    }

    private String fallbackBusArrival(String busStopCode, Exception e) {
        if (mockDataEnabled) {
            logger.warn("Failed to fetch bus arrival from LTA API, using mock data: {}", e.getMessage());
            return mockDataService.getMockBusArrival(busStopCode);
        }

        throw new IllegalStateException("Failed to fetch bus arrival from LTA API", e);
    }

    private record RouteGroupKey(String serviceNo, int direction) {}

    private record BusRouteStop(
            String serviceNo,
            String operator,
            int direction,
            int stopSequence,
            String busStopCode,
            double distance
    ) {}
}
