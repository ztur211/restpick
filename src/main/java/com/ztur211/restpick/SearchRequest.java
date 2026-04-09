package com.ztur211.restpick;

import java.util.List;

// Model class representing the search criteria sent from the frontend to the backend when searching for restaurants
public class SearchRequest {
    private LocationRestriction locationRestriction;
    private double radius; 
    private boolean openNow;
    private List<String> types;
    private List<String> priceLevel;
    private Double rating; // Lowercase d == raw numeric type, uppercase D == wrapper class that can be null

    public LocationRestriction getLocationRestriction() {
        return locationRestriction;
    }
    public void setLocationRestriction(LocationRestriction locationRestriction) {
        this.locationRestriction = locationRestriction;
    }

    public double getRadius() {
        return radius;
    }
    public void setRadius(double radius) {
        this.radius = radius;
    }

    public boolean getOpenNow() {
        return openNow;
    }
    public void setOpenNow(boolean openNow) {
        this.openNow = openNow;
    }

    public List<String> getTypes() {
        return types;
    }
    public void setTypes(List<String> types) {
        this.types = types;
    }

    public List<String> getPriceLevel() {
        return priceLevel;
    }
    public void setPriceLevel(List<String> priceLevel) {
        this.priceLevel = priceLevel;
    }

    public Double getRating() {
        return rating;
    }
    public void setRating(Double rating) {
        this.rating = rating;
    }
    
    public static class LocationRestriction {
        private Circle circle;

        public Circle getCircle() {
            return circle;
        }
        public void setCircle(Circle circle) {
            this.circle = circle;
        }
    }

    public static class Circle {
        private Center center;
        private Double radius;

        public Center getCenter() {
            return center;
        }
        public void setCenter(Center center) {
            this.center = center;
        }

        public Double getRadius() {
            return radius;
        }
        public void setRadius(Double radius) {
            this.radius = radius;
        }
    }

    public static class Center {
        private double latitude;
        private double longitude;

        public double getLatitude() {
            return latitude;
        }
        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }
        public double getLongitude() {
            return longitude;
        }
        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }
    }
}