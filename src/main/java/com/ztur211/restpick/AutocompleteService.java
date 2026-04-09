package com.ztur211.restpick;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AutocompleteService {
    @Value("${google.places.api.key}")
    private String apiKey;

    // Restrict Google Autocomplete to a set language and region
    @Value("${google.places.api.language}")
    private String apiLanguage;

    @Value("${google.places.api.region}")
    private String apiRegion;

    private static final String AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete";
    private static final String PLACE_DETAILS_URL = "https://places.googleapis.com/v1/places/";

    private final RestTemplate restTemplate = new RestTemplate();

    public List<AutocompleteSuggestion> getSuggestions(String input, Double biasLat, Double biasLng) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);

        Map<String, Object> payload = new HashMap<>();
        payload.put("input", input);

        payload.put("languageCode", apiLanguage);
        
        // Separate region codes by comma
        List<String> regionCodes = Arrays.asList(apiRegion.split(","));
        payload.put("includedRegionCodes", regionCodes);

        
        System.out.println("Input: " + input);

        if (biasLat != null && biasLng != null) {
            Map<String, Object> locationBias = Map.of(
                "circle", Map.of(
                    "center", Map.of(
                        "latitude", biasLat, 
                        "longitude", biasLng
                    ),
                    "radius", 50000 // 50 km radius
                )
            );
            // Location bias = bias towards location but not a hard restriction
            // Location restriction = hard restriction to only return results within the specified area
            payload.put("locationBias", locationBias);
        }
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        System.out.println("Request payload and headers: " + payload + headers);

        try {
            ResponseEntity<AutocompleteResponse> response = restTemplate.exchange(
                AUTOCOMPLETE_URL,
                HttpMethod.POST,
                request,
                AutocompleteResponse.class
            );

            AutocompleteResponse body = response.getBody();
            if (body == null || body.getSuggestions() == null) {
                System.out.println("No suggestions found in response: " + response);
                return Collections.emptyList();
            }

            List<AutocompleteSuggestion> results = new ArrayList<>();
            for (AutocompleteResponse.Suggestion suggestion : body.getSuggestions()) {
                AutocompleteResponse.PlacePrediction prediction = suggestion.getPlacePrediction();
                if (prediction != null) {
                    String placeId = prediction.getPlaceId();
                    String address = prediction.getText() != null ? prediction.getText().getText() : "";
                    System.out.println("Suggestion: " + address + " (placeId: " + placeId + ")");
                    results.add(new AutocompleteSuggestion(placeId, address));
                }
            }
            return results;

        } catch (Exception e) {
            throw new RuntimeException("Error fetching autocomplete suggestions: " + e.getMessage());
        }
    }
    public Map<String, Double> getLocation(String placeId) {
        String url = PLACE_DETAILS_URL + placeId + "?fields=location";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "location");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class
            );

            System.out.println("=== RAW PLACE DETAILS RESPONSE ===");
            System.out.println(response.getBody());

            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("location")) {
                throw new RuntimeException("No location data found for place ID: " + placeId);
            }
            System.out.println("Place details response: " + body);

            Map<String, Object> location = (Map<String, Object>) body.get("location");
            return Map.of(
                "latitude", ((Number) location.get("latitude")).doubleValue(),
                "longitude", ((Number) location.get("longitude")).doubleValue()
            );

        } catch (Exception e) {
            throw new RuntimeException("Error fetching place details: " + e.getMessage());
        }

    }
}
