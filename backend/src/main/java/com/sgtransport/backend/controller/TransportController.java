package com.sgtransport.backend.controller;

import com.sgtransport.backend.model.BusStop;
import com.sgtransport.backend.service.LtaApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transport")
@CrossOrigin(origins = {
        "http://localhost:4200",
        "http://127.0.0.1:4200",
        "http://localhost:3000",
        "http://127.0.0.1:3000"
})
public class TransportController {

    @Autowired
    private LtaApiService ltaApiService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\": \"ok\"}");
    }

    @GetMapping("/bus-stops")
    public ResponseEntity<String> getBusStops() {
        String result = ltaApiService.getBusStops();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/bus-arrival/{busStopCode}")
    public ResponseEntity<String> getBusArrival(@PathVariable String busStopCode) {
        String result = ltaApiService.getBusArrival(busStopCode);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<List<BusStop>> searchBusStop(@RequestParam String name) {
        List<BusStop> results = ltaApiService.searchBusStop(name);
        return ResponseEntity.ok(results);
    }
}
