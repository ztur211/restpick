package com.ztur211.restpick;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexControllerTest {

    @Mock
    private RestaurantService restaurantService;

    @Mock
    private AutocompleteService autocompleteService;

    private IndexController controller;

    @BeforeEach
    void setUp() {
        controller = new IndexController(restaurantService, autocompleteService);
        ReflectionTestUtils.setField(controller, "appName", "restpick");
        ReflectionTestUtils.setField(controller, "apiKey", "test-key");
    }

    @Test
    void homePage_returnsIndexView() {
        Model model = new ConcurrentModel();
        String view = controller.homePage(model);

        assertEquals("index", view);
        assertEquals("restpick", model.getAttribute("appName"));
        assertEquals("test-key", model.getAttribute("apiKey"));
        assertEquals(RestaurantService.ALL_CUISINE_TYPES, model.getAttribute("cuisineTypes"));
    }

    @Test
    void autocomplete_withValidInput_returnsOk() {
        List<AutocompleteSuggestion> suggestions = List.of(
                new AutocompleteSuggestion("New York", "NY, USA", "place123")
        );
        when(autocompleteService.getSuggestions("New York", null, null, null))
                .thenReturn(suggestions);

        ResponseEntity<?> response = controller.autocomplete(Map.of("input", "New York"));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        List<AutocompleteSuggestion> body = (List<AutocompleteSuggestion>) response.getBody();
        assertEquals("New York", body.get(0).getMainText());
        assertEquals("place123", body.get(0).getPlaceId());
    }

    @Test
    void autocomplete_withLocationBias_passesCoordinates() {
        when(autocompleteService.getSuggestions("pizza", 40.7, -74.0, null))
                .thenReturn(List.of());

        controller.autocomplete(Map.of("input", "pizza", "biasLatitude", 40.7, "biasLongitude", -74.0));

        verify(autocompleteService).getSuggestions("pizza", 40.7, -74.0, null);
    }

    @Test
    void autocomplete_withMissingInput_returnsBadRequest() {
        ResponseEntity<?> response = controller.autocomplete(Map.of());

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Input is required", body.get("error"));
    }

    @Test
    void autocomplete_withEmptyInput_returnsBadRequest() {
        ResponseEntity<?> response = controller.autocomplete(Map.of("input", ""));

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void autocomplete_serviceThrows_returnsBadRequest() {
        when(autocompleteService.getSuggestions(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("API error"));

        ResponseEntity<?> response = controller.autocomplete(Map.of("input", "test"));

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("API error", body.get("error"));
    }

    @Test
    void pick_returnsRestaurant() {
        Restaurant restaurant = new Restaurant(
                "places/abc123", "Joe's Pizza", "123 Main St", "https://joes.com",
                4.5, 200, "PRICE_LEVEL_MODERATE", 40.7128, -74.0060, "User Address", List.of()
        );
        when(restaurantService.getRandomNearbyRestaurant()).thenReturn(restaurant);

        SearchRequest sr = new SearchRequest();
        ResponseEntity<?> response = controller.pick(sr);

        assertEquals(200, response.getStatusCode().value());
        Restaurant body = (Restaurant) response.getBody();
        assertEquals("Joe's Pizza", body.getDisplayName());
        assertEquals(4.5, body.getRating());
        verify(restaurantService).setSearchRequest(sr);
        verify(restaurantService).getRandomNearbyRestaurant();
    }

    @Test
    void pick_serviceThrows_returnsBadRequest() {
        when(restaurantService.getRandomNearbyRestaurant())
                .thenThrow(new RuntimeException("No restaurants found matching the criteria."));

        ResponseEntity<?> response = controller.pick(new SearchRequest());

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("No restaurants found matching the criteria.", body.get("error"));
    }

    @Test
    void resolveLocation_withValidName_returnsCoordinates() {
        when(autocompleteService.getLocation("place123", null))
                .thenReturn(Map.of("latitude", 40.7128, "longitude", -74.0060));

        ResponseEntity<?> response = controller.resolveLocation(Map.of("name", "place123"));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Double> body = (Map<String, Double>) response.getBody();
        assertEquals(40.7128, body.get("latitude"));
    }

    @Test
    void resolveLocation_withMissingName_returnsBadRequest() {
        ResponseEntity<?> response = controller.resolveLocation(Map.of());

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void resolveLocation_withBlankName_returnsBadRequest() {
        ResponseEntity<?> response = controller.resolveLocation(Map.of("name", "   "));

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void resolveLocation_serviceThrows_returnsBadRequest() {
        when(autocompleteService.getLocation(any(), any()))
                .thenThrow(new RuntimeException("Place not found"));

        ResponseEntity<?> response = controller.resolveLocation(Map.of("name", "invalid"));

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Place not found", body.get("error"));
    }
}
