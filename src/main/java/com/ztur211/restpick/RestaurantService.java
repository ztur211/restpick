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

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "places.name,places.displayName,places.formattedAddress,places.websiteUri,places.rating,places.priceLevel,places.location");


        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        System.out.println("=== GOOGLE PLACES REQUEST BODY ===");
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(payload));

        try {
            // Call Nearby Search
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

             // Fetch details and filter
            List<Restaurant> filtered = new ArrayList<>();
            
            for (Place p : places) {
                // Fetch full details for the selected restaurant
                String detailsUrl = "https://places.googleapis.com/v1/" + p.getName();

                HttpHeaders detailsHeaders = new HttpHeaders();
                detailsHeaders.set("X-Goog-Api-Key", apiKey);
                detailsHeaders.set("X-Goog-FieldMask",
                        "displayName,formattedAddress,websiteUri,rating,userRatingCount,priceLevel,location,photos");

                HttpEntity<Void> detailsRequest = new HttpEntity<>(detailsHeaders);

                ResponseEntity<PlaceDetailsResponse> detailsResponse = restTemplate.exchange(
                        detailsUrl,
                        HttpMethod.GET,
                        detailsRequest,
                        PlaceDetailsResponse.class
                );

                PlaceDetailsResponse details = detailsResponse.getBody();
                if (details == null) continue;

                // Apply rating filter
                Double minRating = currentSearch.getRating();
                if (minRating != null &&
                        (details.getRating() == null || details.getRating() < minRating)) {
                    continue;
                }

                // Apply price filter
                List<String> allowedPrices = currentSearch.getPriceLevel();
                if (allowedPrices != null && !allowedPrices.isEmpty()) {
                    String placePrice = details.getPriceLevel();
                    if (placePrice == null || !allowedPrices.contains(placePrice)) {
                        continue;
                    }
                }
                
                // Get photos
                List<String> photoRefs = new ArrayList<>();
                if (details.getPhotos() != null) {
                    for (PlaceDetailsResponse.Photo photo : details.getPhotos()) {
                        if (photo.getName() != null) {
                            photoRefs.add(photo.getName());
                        }
                    }
                }
                // System.out.println("Photos: " + randomPlace.getPhotos());
                filtered.add(new Restaurant(
                    p.getName(),
                    details.getDisplayName().getText(),
                    details.getFormattedAddress(),
                    details.getWebsiteUri(),
                    details.getRating(),
                    details.getUserRatingCount(),
                    details.getPriceLevel(),
                    details.getLocation().getLatitude(),
                    details.getLocation().getLongitude(),
                    currentSearch.getUserAddress(),
                    photoRefs
                ));
            }

            if (filtered.isEmpty()) {
                throw new RuntimeException("No restaurants matched rating/price filters.");
            }
            
            return filtered.get(new Random().nextInt(filtered.size()));

        } catch (Exception e) {
            throw new RuntimeException("Error fetching restaurants: " + e.getMessage());
        }
    }
}