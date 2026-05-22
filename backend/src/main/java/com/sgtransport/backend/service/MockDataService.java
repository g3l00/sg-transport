package com.sgtransport.backend.service;

import org.springframework.stereotype.Service;

@Service
public class MockDataService {

    public String getMockBusStops() {
        return """
            {
              "odata.metadata": "mock",
              "value": [
                {
                  "BusStopCode": "01012",
                  "RoadName": "Victoria Street",
                  "Description": "Opp Tong Watt Road",
                  "Latitude": 1.2926,
                  "Longitude": 103.8527
                },
                {
                  "BusStopCode": "01013",
                  "RoadName": "Victoria Street",
                  "Description": "Opp National Library",
                  "Latitude": 1.2942,
                  "Longitude": 103.8515
                },
                {
                  "BusStopCode": "01014",
                  "RoadName": "Queen Street",
                  "Description": "Bef Rochor Road",
                  "Latitude": 1.2963,
                  "Longitude": 103.8545
                }
              ]
            }
            """;
    }

    public String getMockBusArrival(String busStopCode) {
        return """
            {
              "odata.metadata": "mock",
              "BusStopCode": "%s",
              "Services": [
                {
                  "ServiceNo": "2",
                  "Operator": "SBS",
                  "NextBus": {
                    "OriginCode": "01012",
                    "DestinationCode": "97009",
                    "EstimatedArrival": "2026-05-21T04:45:00+08:00",
                    "Latitude": 1.3456,
                    "Longitude": 103.8901,
                    "VisitNumber": "1",
                    "Load": "Seats Available",
                    "Feature": "WAB"
                  },
                  "NextBus2": {
                    "OriginCode": "01012",
                    "DestinationCode": "97009",
                    "EstimatedArrival": "2026-05-21T05:00:00+08:00",
                    "Latitude": 0.0,
                    "Longitude": 0.0,
                    "VisitNumber": "2",
                    "Load": "Standing Room Only",
                    "Feature": "WAB"
                  }
                },
                {
                  "ServiceNo": "5",
                  "Operator": "SBS",
                  "NextBus": {
                    "OriginCode": "01012",
                    "DestinationCode": "87009",
                    "EstimatedArrival": "2026-05-21T04:52:00+08:00",
                    "Latitude": 1.3200,
                    "Longitude": 103.8800,
                    "VisitNumber": "1",
                    "Load": "Seats Available",
                    "Feature": ""
                  }
                }
              ]
            }
            """.formatted(busStopCode);
    }
}
