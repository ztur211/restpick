package com.ztur211.restpick;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutocompleteServiceTest {

    private MockWebServer mockWebServer;
    private AutocompleteService autocompleteService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        autocompleteService = new AutocompleteService();
        ReflectionTestUtils.setField(autocompleteService, "webClient", webClient);
        ReflectionTestUtils.setField(autocompleteService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(autocompleteService, "apiLanguage", "en");
        ReflectionTestUtils.setField(autocompleteService, "apiRegion", "us");
        ReflectionTestUtils.setField(autocompleteService, "baseUrl", mockWebServer.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getSuggestions_returnsParsedSuggestions() {
        String responseJson = """
                {
                    "suggestions": [
                        {
                            "placePrediction": {
                                "placeId": "placeId1",
                                "structuredFormat": {
                                    "mainText": {"text": "New York"},
                                    "secondaryText": {"text": "NY, USA"}
                                }
                            }
                        },
                        {
                            "placePrediction": {
                                "placeId": "placeId2",
                                "structuredFormat": {
                                    "mainText": {"text": "New Orleans"},
                                    "secondaryText": {"text": "LA, USA"}
                                }
                            }
                        }
                    ]
                }""";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("New", null, null);

        assertEquals(2, results.size());
        assertEquals("New York", results.get(0).getMainText());
        assertEquals("NY, USA", results.get(0).getSecondaryText());
        assertEquals("placeId1", results.get(0).getPlaceId());
        assertEquals("New Orleans", results.get(1).getMainText());
    }

    @Test
    void getSuggestions_nullBody_returnsEmptyList() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("null"));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void getSuggestions_nullSuggestions_returnsEmptyList() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void getSuggestions_skipsNullPredictions() {
        String responseJson = """
                {
                    "suggestions": [
                        {"placePrediction": null},
                        {
                            "placePrediction": {
                                "placeId": "id1",
                                "structuredFormat": {
                                    "mainText": {"text": "Valid"},
                                    "secondaryText": {"text": "Place"}
                                }
                            }
                        }
                    ]
                }""";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertEquals(1, results.size());
        assertEquals("Valid", results.get(0).getMainText());
    }

    @Test
    void getSuggestions_handlesNullStructuredFormat() {
        String responseJson = """
                {
                    "suggestions": [
                        {
                            "placePrediction": {
                                "placeId": "id1",
                                "structuredFormat": null
                            }
                        }
                    ]
                }""";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertEquals(1, results.size());
        assertEquals("", results.get(0).getMainText());
        assertEquals("", results.get(0).getSecondaryText());
    }

    @Test
    void getSuggestions_multipleRegionCodes() {
        ReflectionTestUtils.setField(autocompleteService, "apiRegion", "us,ca,mx");

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"suggestions\":[]}"));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertNotNull(results);
    }

    @Test
    void getLocation_returnsCoordinates() {
        String responseJson = """
                {
                    "location": {"latitude": 40.7128, "longitude": -74.006}
                }""";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        Map<String, Double> result = autocompleteService.getLocation("placeId123");

        assertEquals(40.7128, result.get("latitude"));
        assertEquals(-74.006, result.get("longitude"));
    }

    @Test
    void getLocation_nullBody_throws() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("null"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> autocompleteService.getLocation("placeId123"));
        assertTrue(ex.getMessage().contains("Error fetching place details"));
    }

    @Test
    void getLocation_noLocationKey_throws() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> autocompleteService.getLocation("placeId123"));
        assertTrue(ex.getMessage().contains("Error fetching place details"));
    }
}
