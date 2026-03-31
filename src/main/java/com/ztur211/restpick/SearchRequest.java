package com.ztur211.restpick;

import java.util.List;

// Model class representing the search criteria sent from the frontend to the backend when searching for restaurants
public class SearchRequest {
    private Location location;
    private double radiusMiles = 5.0; // Set as default radius
    private boolean openNow = false; // Set as default open status
    private List<String> cuisineTypes;
    private List<String> priceLevels;
    private Double minRating; // Lowercase d == raw numeric type, uppercase D == wrapper class that can be null

    public Location getLocation() {
        return location;
    }
    public void setLocation(Location location) {
        this.location = location;
    }

    public double getRadiusMiles() {
        return radiusMiles;
    }
    public void setRadiusMiles(double radiusMiles) {
        this.radiusMiles = radiusMiles;
    }

    public boolean isOpenNow() {
        return openNow;
    }
    public void setOpenNow(boolean openNow) {
        this.openNow = openNow;
    }

    public List<String> getCuisineTypes() {
        return cuisineTypes;
    }
    public void setCuisineTypes(List<String> cuisineTypes) {
        this.cuisineTypes = cuisineTypes;
    }

    public List<String> getPriceLevels() {
        return priceLevels;
    }
    public void setPriceLevels(List<String> priceLevels) {
        this.priceLevels = priceLevels;
    }

    public Double getMinRating() {
        return minRating;
    }
    public void setMinRating(Double minRating) {
        this.minRating = minRating;
    }
    
    public static class Location {
        private double lat;
        private double lng;

        public double getLat() {
            return lat;
        }
        public void setLat(double lat) {
            this.lat = lat;
        }
        public double getLng() {
            return lng;
        }
        public void setLng(double lng) {
            this.lng = lng;
        }
    }
}