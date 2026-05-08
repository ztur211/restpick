package com.ztur211.restpick;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchRequestTest {

    @Test
    void searchRequest_gettersAndSetters() {
        SearchRequest sr = new SearchRequest();
        sr.setRadius(10.0);
        sr.setOpenNow(true);
        sr.setTypes(List.of("italian_restaurant", "pizza_restaurant"));
        sr.setPriceLevel(List.of("PRICE_LEVEL_MODERATE"));
        sr.setRating(4.0);
        sr.setUserAddress("123 Main St");

        assertEquals(10.0, sr.getRadius());
        assertTrue(sr.getOpenNow());
        assertEquals(2, sr.getTypes().size());
        assertEquals(List.of("PRICE_LEVEL_MODERATE"), sr.getPriceLevel());
        assertEquals(4.0, sr.getRating());
        assertEquals("123 Main St", sr.getUserAddress());
    }

    @Test
    void locationRestriction_nestedStructure() {
        SearchRequest.Center center = new SearchRequest.Center();
        center.setLatitude(40.7128);
        center.setLongitude(-74.0060);

        SearchRequest.Circle circle = new SearchRequest.Circle();
        circle.setCenter(center);
        circle.setRadius(5.0);

        SearchRequest.LocationRestriction lr = new SearchRequest.LocationRestriction();
        lr.setCircle(circle);

        SearchRequest sr = new SearchRequest();
        sr.setLocationRestriction(lr);

        assertEquals(40.7128, sr.getLocationRestriction().getCircle().getCenter().getLatitude());
        assertEquals(-74.006, sr.getLocationRestriction().getCircle().getCenter().getLongitude());
        assertEquals(5.0, sr.getLocationRestriction().getCircle().getRadius());
    }

    @Test
    void rating_canBeNull() {
        SearchRequest sr = new SearchRequest();
        assertNull(sr.getRating());
    }
}
