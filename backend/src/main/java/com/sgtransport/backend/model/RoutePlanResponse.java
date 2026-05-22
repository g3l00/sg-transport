package com.sgtransport.backend.model;

import java.util.List;

public record RoutePlanResponse(
        BusStop from,
        BusStop to,
        List<RouteOption> routes
) {
    public record RouteOption(
            String type,
            BusStop transferStop,
            List<RouteLeg> legs,
            double totalDistanceKm,
            int totalStops
    ) {}

    public record RouteLeg(
            String serviceNo,
            String operator,
            int direction,
            BusStop from,
            BusStop to,
            int fromSequence,
            int toSequence,
            double distanceKm,
            int stops
    ) {}
}
