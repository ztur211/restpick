package com.ztur211.restpick;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RestaurantServiceTest {

    private static final String SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby";
    private static final String DETAILS_BASE = "https://places.googleapis.com/v1/";

    private RestaurantService restaurantService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantService();
        ReflectionTestUtils.setField(restaurantService, "apiKey", "test-api-key");
        // Bind MockRestServiceServer to the service's real RestTemplate (no production change).
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(restaurantService, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void getRandomNearbyRestaurant_noSearchRequest_throws() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restaurantService.getRandomNearbyRestaurant());
        assertTrue(ex.getMessage().contains("No address selected"));
    }

    @Test
    void getRandomNearbyRestaurant_noResultsFromApi_throws() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        restaurantService.setSearchRequest(request);

        mockServer.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"places\":[]}", MediaType.APPLICATION_JSON));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restaurantService.getRandomNearbyRestaurant());
        assertTrue(ex.getMessage().contains("No restaurants found"));
        mockServer.verify();
    }

    @Test
    void getRandomNearbyRestaurant_returnsRestaurant() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        request.setTypes(List.of("italian_restaurant"));
        restaurantService.setSearchRequest(request);

        String searchResponse = """
                {"places":[{"name":"places/abc123"}]}""";
        mockServer.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(searchResponse, MediaType.APPLICATION_JSON));

        String detailsResponse = """
                {
                    "displayName":{"text":"Test Restaurant"},
                    "formattedAddress":"123 Main St",
                    "websiteUri":"https://test.com",
                    "rating":4.5,
                    "userRatingCount":100,
                    "priceLevel":"PRICE_LEVEL_MODERATE",
                    "location":{"latitude":40.7,"longitude":-74.0},
                    "photos":[]
                }""";
        mockServer.expect(requestTo(DETAILS_BASE + "places/abc123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(detailsResponse, MediaType.APPLICATION_JSON));

        Restaurant result = restaurantService.getRandomNearbyRestaurant();

        assertNotNull(result);
        assertEquals("Test Restaurant", result.getDisplayName());
        assertEquals("123 Main St", result.getFormattedAddress());
        assertEquals(4.5, result.getRating());
        mockServer.verify();
    }

    @Test
    void getRandomNearbyRestaurant_defaultCuisineWhenNoneSelected() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        request.setTypes(null);
        restaurantService.setSearchRequest(request);

        String searchResponse = """
                {"places":[{"name":"places/abc123"}]}""";
        mockServer.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.includedTypes[0]").value("restaurant"))
                .andRespond(withSuccess(searchResponse, MediaType.APPLICATION_JSON));

        String detailsResponse = """
                {
                    "displayName":{"text":"Generic Place"},
                    "formattedAddress":"456 Elm St",
                    "rating":4.0,
                    "userRatingCount":50,
                    "location":{"latitude":40.7,"longitude":-74.0},
                    "photos":[]
                }""";
        mockServer.expect(requestTo(DETAILS_BASE + "places/abc123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(detailsResponse, MediaType.APPLICATION_JSON));

        Restaurant result = restaurantService.getRandomNearbyRestaurant();
        assertNotNull(result);
        mockServer.verify();
    }

    @Test
    void getRandomNearbyRestaurant_filtersOutLowRating() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        request.setRating(4.0);
        restaurantService.setSearchRequest(request);

        mockServer.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"places\":[{\"name\":\"places/low-rated\"}]}", MediaType.APPLICATION_JSON));

        String detailsResponse = """
                {
                    "displayName":{"text":"Bad Place"},
                    "formattedAddress":"789 Oak St",
                    "rating":2.5,
                    "userRatingCount":10,
                    "location":{"latitude":40.7,"longitude":-74.0},
                    "photos":[]
                }""";
        mockServer.expect(requestTo(DETAILS_BASE + "places/low-rated"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(detailsResponse, MediaType.APPLICATION_JSON));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restaurantService.getRandomNearbyRestaurant());
        assertTrue(ex.getMessage().contains("No restaurants matched rating/price filters"));
        mockServer.verify();
    }

    @Test
    void getRandomNearbyRestaurant_filtersOutWrongPriceLevel() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        request.setPriceLevel(List.of("PRICE_LEVEL_INEXPENSIVE"));
        restaurantService.setSearchRequest(request);

        mockServer.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"places\":[{\"name\":\"places/expensive\"}]}", MediaType.APPLICATION_JSON));

        String detailsResponse = """
                {
                    "displayName":{"text":"Fancy Place"},
                    "formattedAddress":"1 Rich Ave",
                    "rating":4.8,
                    "userRatingCount":500,
                    "priceLevel":"PRICE_LEVEL_EXPENSIVE",
                    "location":{"latitude":40.7,"longitude":-74.0},
                    "photos":[]
                }""";
        mockServer.expect(requestTo(DETAILS_BASE + "places/expensive"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(detailsResponse, MediaType.APPLICATION_JSON));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restaurantService.getRandomNearbyRestaurant());
        assertTrue(ex.getMessage().contains("No restaurants matched rating/price filters"));
        mockServer.verify();
    }

    @Test
    void getRandomNearbyRestaurant_includesPhotos() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        restaurantService.setSearchRequest(request);

        mockServer.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"places\":[{\"name\":\"places/with-photos\"}]}", MediaType.APPLICATION_JSON));

        String detailsResponse = """
                {
                    "displayName":{"text":"Photo Place"},
                    "formattedAddress":"5 Pic Lane",
                    "rating":4.0,
                    "userRatingCount":80,
                    "location":{"latitude":40.7,"longitude":-74.0},
                    "photos":[{"name":"places/photos/ref1"},{"name":"places/photos/ref2"}]
                }""";
        mockServer.expect(requestTo(DETAILS_BASE + "places/with-photos"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(detailsResponse, MediaType.APPLICATION_JSON));

        Restaurant result = restaurantService.getRandomNearbyRestaurant();
        assertEquals(2, result.getPhotos().size());
        assertEquals("places/photos/ref1", result.getPhotos().get(0));
        mockServer.verify();
    }

    @Test
    void allCuisineTypes_hasExpectedSize() {
        assertEquals(21, RestaurantService.ALL_CUISINE_TYPES.size());
        assertTrue(RestaurantService.ALL_CUISINE_TYPES.contains("italian_restaurant"));
        assertTrue(RestaurantService.ALL_CUISINE_TYPES.contains("sushi_restaurant"));
    }

    private SearchRequest buildSearchRequest(double lat, double lng, double radius) {
        SearchRequest.Center center = new SearchRequest.Center();
        center.setLatitude(lat);
        center.setLongitude(lng);

        SearchRequest.Circle circle = new SearchRequest.Circle();
        circle.setCenter(center);
        circle.setRadius(radius);

        SearchRequest.LocationRestriction lr = new SearchRequest.LocationRestriction();
        lr.setCircle(circle);

        SearchRequest request = new SearchRequest();
        request.setLocationRestriction(lr);
        request.setRadius(radius);
        request.setUserAddress("Test Address");
        return request;
    }
}
