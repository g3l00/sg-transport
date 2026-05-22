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

    public String getMockBusRoutes() {
        return """
            {
              "odata.metadata": "mock",
              "value": [
                {
                  "ServiceNo": "2",
                  "Operator": "SBST",
                  "Direction": 1,
                  "StopSequence": 1,
                  "BusStopCode": "01012",
                  "Distance": 0.0,
                  "WD_FirstBus": "0520",
                  "WD_LastBus": "2350",
                  "SAT_FirstBus": "0520",
                  "SAT_LastBus": "2350",
                  "SUN_FirstBus": "0600",
                  "SUN_LastBus": "2350"
                },
                {
                  "ServiceNo": "2",
                  "Operator": "SBST",
                  "Direction": 1,
                  "StopSequence": 2,
                  "BusStopCode": "01013",
                  "Distance": 0.6,
                  "WD_FirstBus": "0523",
                  "WD_LastBus": "2353",
                  "SAT_FirstBus": "0523",
                  "SAT_LastBus": "2353",
                  "SUN_FirstBus": "0603",
                  "SUN_LastBus": "2353"
                },
                {
                  "ServiceNo": "2",
                  "Operator": "SBST",
                  "Direction": 1,
                  "StopSequence": 3,
                  "BusStopCode": "01014",
                  "Distance": 1.2,
                  "WD_FirstBus": "0526",
                  "WD_LastBus": "2356",
                  "SAT_FirstBus": "0526",
                  "SAT_LastBus": "2356",
                  "SUN_FirstBus": "0606",
                  "SUN_LastBus": "2356"
                },
                {
                  "ServiceNo": "5",
                  "Operator": "SBST",
                  "Direction": 1,
                  "StopSequence": 1,
                  "BusStopCode": "01012",
                  "Distance": 0.0,
                  "WD_FirstBus": "0530",
                  "WD_LastBus": "2340",
                  "SAT_FirstBus": "0530",
                  "SAT_LastBus": "2340",
                  "SUN_FirstBus": "0605",
                  "SUN_LastBus": "2340"
                },
                {
                  "ServiceNo": "5",
                  "Operator": "SBST",
                  "Direction": 1,
                  "StopSequence": 2,
                  "BusStopCode": "01013",
                  "Distance": 0.5,
                  "WD_FirstBus": "0533",
                  "WD_LastBus": "2343",
                  "SAT_FirstBus": "0533",
                  "SAT_LastBus": "2343",
                  "SUN_FirstBus": "0608",
                  "SUN_LastBus": "2343"
                },
                {
                  "ServiceNo": "7",
                  "Operator": "SBST",
                  "Direction": 1,
                  "StopSequence": 1,
                  "BusStopCode": "01013",
                  "Distance": 0.0,
                  "WD_FirstBus": "0540",
                  "WD_LastBus": "2335",
                  "SAT_FirstBus": "0540",
                  "SAT_LastBus": "2335",
                  "SUN_FirstBus": "0610",
                  "SUN_LastBus": "2335"
                },
                {
                  "ServiceNo": "7",
                  "Operator": "SBST",
                  "Direction": 1,
                  "StopSequence": 2,
                  "BusStopCode": "01014",
                  "Distance": 0.7,
                  "WD_FirstBus": "0544",
                  "WD_LastBus": "2339",
                  "SAT_FirstBus": "0544",
                  "SAT_LastBus": "2339",
                  "SUN_FirstBus": "0614",
                  "SUN_LastBus": "2339"
                }
              ]
            }
            """;
    }
}
