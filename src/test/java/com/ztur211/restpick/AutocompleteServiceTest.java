package com.ztur211.restpick;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AutocompleteServiceTest {

    private static final String AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete";
    private static final String PLACE_DETAILS_URL = "https://places.googleapis.com/v1/places/";

    private AutocompleteService autocompleteService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        autocompleteService = new AutocompleteService();
        ReflectionTestUtils.setField(autocompleteService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(autocompleteService, "apiLanguage", "en");
        ReflectionTestUtils.setField(autocompleteService, "apiRegion", "us");
        // Bind MockRestServiceServer to the service's real RestTemplate (no production change).
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(autocompleteService, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
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
        mockServer.expect(requestTo(AUTOCOMPLETE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("New", null, null);

        assertEquals(2, results.size());
        assertEquals("New York", results.get(0).getMainText());
        assertEquals("NY, USA", results.get(0).getSecondaryText());
        assertEquals("placeId1", results.get(0).getPlaceId());
        assertEquals("New Orleans", results.get(1).getMainText());
        mockServer.verify();
    }

    @Test
    void getSuggestions_nullBody_returnsEmptyList() {
        mockServer.expect(requestTo(AUTOCOMPLETE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertTrue(results.isEmpty());
        mockServer.verify();
    }

    @Test
    void getSuggestions_nullSuggestions_returnsEmptyList() {
        mockServer.expect(requestTo(AUTOCOMPLETE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertTrue(results.isEmpty());
        mockServer.verify();
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
        mockServer.expect(requestTo(AUTOCOMPLETE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertEquals(1, results.size());
        assertEquals("Valid", results.get(0).getMainText());
        mockServer.verify();
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
        mockServer.expect(requestTo(AUTOCOMPLETE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertEquals(1, results.size());
        assertEquals("", results.get(0).getMainText());
        assertEquals("", results.get(0).getSecondaryText());
        mockServer.verify();
    }

    @Test
    void getSuggestions_multipleRegionCodes() {
        ReflectionTestUtils.setField(autocompleteService, "apiRegion", "us,ca,mx");

        mockServer.expect(requestTo(AUTOCOMPLETE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"suggestions\":[]}", MediaType.APPLICATION_JSON));

        List<AutocompleteSuggestion> results = autocompleteService.getSuggestions("test", null, null);
        assertNotNull(results);
        mockServer.verify();
    }

    @Test
    void getLocation_returnsCoordinates() {
        String responseJson = """
                {
                    "location": {"latitude": 40.7128, "longitude": -74.006}
                }""";
        mockServer.expect(requestTo(PLACE_DETAILS_URL + "placeId123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        Map<String, Double> result = autocompleteService.getLocation("placeId123");

        assertEquals(40.7128, result.get("latitude"));
        assertEquals(-74.006, result.get("longitude"));
        mockServer.verify();
    }

    @Test
    void getLocation_nullBody_throws() {
        mockServer.expect(requestTo(PLACE_DETAILS_URL + "placeId123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> autocompleteService.getLocation("placeId123"));
        assertTrue(ex.getMessage().contains("Error fetching place details"));
        mockServer.verify();
    }

    @Test
    void getLocation_noLocationKey_throws() {
        mockServer.expect(requestTo(PLACE_DETAILS_URL + "placeId123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> autocompleteService.getLocation("placeId123"));
        assertTrue(ex.getMessage().contains("Error fetching place details"));
        mockServer.verify();
    }
}
