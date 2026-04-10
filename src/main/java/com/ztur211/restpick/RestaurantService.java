package com.ztur211.restpick;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

// Service class that contains the business logic for fetching nearby restaurants based on user criteria and picking a random one
@Service
public class RestaurantService {

    @Value("${google.places.api.key}")
    private String apiKey;

    private static final String SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby";
    private static final int MAX_RESULTS = 20;

    // https://developers.google.com/maps/documentation/places/web-service/place-types#food-and-drink
    public static final List<String> ALL_CUISINE_TYPES = List.of(
        "american_restaurant", "barbecue_restaurant", "chinese_restaurant", "french_restaurant", "hamburger_restaurant", "indian_restaurant", "italian_restaurant", "japanese_restaurant", "mexican_restaurant", "pizza_restaurant", "seafood_restaurant", "steak_house", "sushi_restaurant", "thai_restaurant", "vegetarian_restaurant", "vegan_restaurant", "mediterranean_restaurant", "korean_restaurant", "vietnamese_restaurant", "spanish_restaurant", "greek_restaurant"
    );

    private final RestTemplate restTemplate = new RestTemplate();
    private SearchRequest currentSearch;

    public void setSearchRequest(SearchRequest searchRequest) {
        this.currentSearch = searchRequest;
    }

    public Restaurant getRandomNearbyRestaurant() {
        if (currentSearch == null) {
            throw new RuntimeException("No address selected. Please select an address first.");
        }

        // Get fields from SearchRequest
        SearchRequest.LocationRestriction lr = currentSearch.getLocationRestriction();
        SearchRequest.Circle circle = lr.getCircle();
        SearchRequest.Center center = circle.getCenter();

        double latitude = center.getLatitude();
        double longitude = center.getLongitude();
        double radiusMeters = circle.getRadius() * 1609.34; // Miles to meters

        // Use selected cuisine types or default to "restaurant" if none are selected
        List<String> cuisines = currentSearch.getTypes();
        if (cuisines == null || cuisines.isEmpty()) {
            cuisines = List.of("restaurant");
        }

        // Google Places API request body
        Map<String, Object> payload = new HashMap<>();
        payload.put("includedTypes", cuisines);
        payload.put("maxResultCount", MAX_RESULTS);
        payload.put("locationRestriction", Map.of(
            "circle", Map.of(
                "center", Map.of("latitude", latitude, "longitude", longitude), 
                "radius", radiusMeters
            )
        ));

        Boolean openNow = currentSearch.getOpenNow();
        if (openNow != null && openNow) {
            payload.put("openNow", true);
        }

        if (currentSearch.getPriceLevel() != null && !currentSearch.getPriceLevel().isEmpty()) {
            payload.put("priceLevel", currentSearch.getPriceLevel());
        }

        if (currentSearch.getRating() != null) {
            payload.put("rating", currentSearch.getRating());
        }

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.websiteUri," + "places.rating,places.priceLevel,places.location");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        System.out.println("=== GOOGLE PLACES REQUEST BODY ===");
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(payload));

        try {
            ResponseEntity<PlacesResponse> response = restTemplate.postForEntity(
                SEARCH_URL, 
                request, 
                PlacesResponse.class
            );
            System.out.println("=== GOOGLE PLACES RAW RESPONSE ===");
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(response.getBody()));


            List<Place> places = response.getBody().getPlaces();

            if (places == null || places.isEmpty()) {
                throw new RuntimeException("No restaurants found matching the criteria.");
            }

            if (currentSearch.getRating() != null) {
                places = places.stream()
                    .filter(p -> p.getRating() != null && p.getRating() >= currentSearch.getRating())
                    .collect(Collectors.toList());
            }

            if (places.isEmpty()) {
                throw new RuntimeException("No restaurants found matching the criteria after applying rating filter.");
            }

            // Pick random restaurant
            Place randomPlace = places.get(new Random().nextInt(places.size()));

            String mapUrl =
                "https://maps.googleapis.com/maps/api/staticmap"
                + "?center=" + randomPlace.getLocation().getLatitude() + "," + randomPlace.getLocation().getLongitude()
                + "&zoom=17"
                + "&size=600x300"
                + "&maptype=satellite"
                + "&markers=color:red%7C" + randomPlace.getLocation().getLatitude() + "," + randomPlace.getLocation().getLongitude()
                + "&key=" + apiKey;
            
            return new Restaurant(
                randomPlace.getDisplayName().getText(),
                randomPlace.getFormattedAddress(),
                randomPlace.getWebsiteUri(),
                randomPlace.getRating(),
                randomPlace.getPriceLevel(),
                randomPlace.getLocation().getLatitude(),
                randomPlace.getLocation().getLongitude(),
                mapUrl
            );

        } catch (Exception e) {
            throw new RuntimeException("Error fetching restaurants: " + e.getMessage());
        }
    }
}