package com.ztur211.restpick;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

// Service class that contains the business logic for fetching nearby restaurants based on user criteria and picking a random one
@Service
public class RestaurantService {

    @Value("${google.places.api.key}")
    private String apiKey;

    private static final String SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby";
    private static final double METERS_IN_MILE = 1609.34;
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
            throw new RuntimeException("No address selected. Please select and address first.");
        }

        double lat = currentSearch.getLocation().getLat();
        double lng = currentSearch.getLocation().getLng();
        double radiusMeters = currentSearch.getRadiusMiles() * METERS_IN_MILE;
        // Use selected cuisine types or default to "restaurant" if none are selected
        List<String> selectedCuisines = currentSearch.getCuisineTypes() != null && !currentSearch.getCuisineTypes().isEmpty() ? currentSearch.getCuisineTypes() : List.of("restaurant");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.websiteUri,places.rating,places.priceLevel,places.regularOpenHours");

        Map<String, Object> payload = new HashMap<>();
        payload.put("includedTypes", selectedCuisines);
        payload.put("maxResultCount", MAX_RESULTS);
        payload.put("locationRestrictions", Map.of(
            "circle", Map.of(
                "center", Map.of("latitude", lat, "longitude", lng), 
                "radius", radiusMeters
            )
        ));

        if (currentSearch.isOpenNow()) {
            payload.put("openNow", true);
        }

        if (currentSearch.getPriceLevels() != null && !currentSearch.getPriceLevels().isEmpty()) {
            payload.put("priceLevels", currentSearch.getPriceLevels());
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<PlacesResponse> response = restTemplate.postForEntity(
                SEARCH_URL, 
                request, 
                PlacesResponse.class
            );

            List<Place> places = response.getBody().getPlaces();

            if (places == null || places.isEmpty()) {
                throw new RuntimeException("No restaurants found matching the criteria.");
            }

            if (currentSearch.getMinRating() != null) {
                places = places.stream()
                    .filter(p -> p.getRating() != null && p.getRating() >= currentSearch.getMinRating())
                    .collect(Collectors.toList());
            }

            if (places.isEmpty()) {
                throw new RuntimeException("No restaurants found matching the criteria after applying rating filter.");
            }

            Place randomPlace = places.get(new Random().nextInt(places.size()));
            
            return new Restaurant(
                randomPlace.getDisplayName().getText(),
                randomPlace.getFormattedAddress(),
                randomPlace.getWebsiteUri(),
                randomPlace.getRating(),
                randomPlace.getPriceLevel()
            );

        } catch (Exception e) {
            throw new RuntimeException("Error fetching restaurants: " + e.getMessage());
        }
    }
}