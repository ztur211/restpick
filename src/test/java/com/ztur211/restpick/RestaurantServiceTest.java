package com.ztur211.restpick;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantServiceTest {

    private MockWebServer mockWebServer;
    private RestaurantService restaurantService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        restaurantService = new RestaurantService();
        ReflectionTestUtils.setField(restaurantService, "webClient", webClient);
        ReflectionTestUtils.setField(restaurantService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(restaurantService, "baseUrl", mockWebServer.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
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

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"places\":[]}"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restaurantService.getRandomNearbyRestaurant());
        assertTrue(ex.getMessage().contains("No restaurants found"));
    }

    @Test
    void getRandomNearbyRestaurant_returnsRestaurant() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        request.setTypes(List.of("italian_restaurant"));
        restaurantService.setSearchRequest(request);

        String searchResponse = """
                {"places":[{"name":"places/abc123"}]}""";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(searchResponse));

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
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(detailsResponse));

        Restaurant result = restaurantService.getRandomNearbyRestaurant();

        assertNotNull(result);
        assertEquals("Test Restaurant", result.getDisplayName());
        assertEquals("123 Main St", result.getFormattedAddress());
        assertEquals(4.5, result.getRating());
    }

    @Test
    void getRandomNearbyRestaurant_defaultCuisineWhenNoneSelected() throws Exception {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        request.setTypes(null);
        restaurantService.setSearchRequest(request);

        String searchResponse = """
                {"places":[{"name":"places/abc123"}]}""";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(searchResponse));

        String detailsResponse = """
                {
                    "displayName":{"text":"Generic Place"},
                    "formattedAddress":"456 Elm St",
                    "rating":4.0,
                    "userRatingCount":50,
                    "location":{"latitude":40.7,"longitude":-74.0},
                    "photos":[]
                }""";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(detailsResponse));

        Restaurant result = restaurantService.getRandomNearbyRestaurant();
        assertNotNull(result);

        var recordedRequest = mockWebServer.takeRequest();
        String requestBody = recordedRequest.getBody().readUtf8();
        assertTrue(requestBody.contains("\"restaurant\""));
    }

    @Test
    void getRandomNearbyRestaurant_filtersOutLowRating() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        request.setRating(4.0);
        restaurantService.setSearchRequest(request);

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"places\":[{\"name\":\"places/low-rated\"}]}"));

        String detailsResponse = """
                {
                    "displayName":{"text":"Bad Place"},
                    "formattedAddress":"789 Oak St",
                    "rating":2.5,
                    "userRatingCount":10,
                    "location":{"latitude":40.7,"longitude":-74.0},
                    "photos":[]
                }""";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(detailsResponse));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restaurantService.getRandomNearbyRestaurant());
        assertTrue(ex.getMessage().contains("No restaurants matched rating/price filters"));
    }

    @Test
    void getRandomNearbyRestaurant_filtersOutWrongPriceLevel() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        request.setPriceLevel(List.of("PRICE_LEVEL_INEXPENSIVE"));
        restaurantService.setSearchRequest(request);

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"places\":[{\"name\":\"places/expensive\"}]}"));

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
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(detailsResponse));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> restaurantService.getRandomNearbyRestaurant());
        assertTrue(ex.getMessage().contains("No restaurants matched rating/price filters"));
    }

    @Test
    void getRandomNearbyRestaurant_includesPhotos() {
        SearchRequest request = buildSearchRequest(40.7, -74.0, 5.0);
        restaurantService.setSearchRequest(request);

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"places\":[{\"name\":\"places/with-photos\"}]}"));

        String detailsResponse = """
                {
                    "displayName":{"text":"Photo Place"},
                    "formattedAddress":"5 Pic Lane",
                    "rating":4.0,
                    "userRatingCount":80,
                    "location":{"latitude":40.7,"longitude":-74.0},
                    "photos":[{"name":"places/photos/ref1"},{"name":"places/photos/ref2"}]
                }""";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(detailsResponse));

        Restaurant result = restaurantService.getRandomNearbyRestaurant();
        assertEquals(2, result.getPhotos().size());
        assertEquals("places/photos/ref1", result.getPhotos().get(0));
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
