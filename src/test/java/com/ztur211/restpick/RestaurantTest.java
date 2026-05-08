package com.ztur211.restpick;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantTest {

    @Test
    void constructor_setsAllFields() {
        List<String> photos = List.of("photo1", "photo2");
        Restaurant r = new Restaurant(
                "places/abc", "Test Name", "123 Main St", "https://test.com",
                4.5, 200, "PRICE_LEVEL_MODERATE", 40.7128, -74.0060, "Origin St", photos
        );

        assertEquals("places/abc", r.getName());
        assertEquals("Test Name", r.getDisplayName());
        assertEquals("123 Main St", r.getFormattedAddress());
        assertEquals("https://test.com", r.getWebsiteUri());
        assertEquals(4.5, r.getRating());
        assertEquals(200, r.getRatingCount());
        assertEquals("PRICE_LEVEL_MODERATE", r.getPriceLevel());
        assertEquals(40.7128, r.getLatitude());
        assertEquals(-74.006, r.getLongitude());
        assertEquals("Origin St", r.getOriginAddress());
        assertEquals(photos, r.getPhotos());
    }

    @Test
    void constructor_handlesNullValues() {
        Restaurant r = new Restaurant(
                "places/abc", "Name", null, null,
                null, null, null, 0.0, 0.0, null, null
        );

        assertNull(r.getFormattedAddress());
        assertNull(r.getWebsiteUri());
        assertNull(r.getRating());
        assertNull(r.getRatingCount());
        assertNull(r.getPriceLevel());
        assertNull(r.getOriginAddress());
        assertNull(r.getPhotos());
    }
}
