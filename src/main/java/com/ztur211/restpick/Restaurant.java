package com.ztur211.restpick;

// Model class representing a restaurant with relevant details for the frontend
public class Restaurant {
    private String displayName;
    private String formattedAddress;
    private String websiteUri;
    private Double rating;
    private String priceLevel;
    private Double latitude;
    private Double longitude;
    private String mapUrl;

    public Restaurant(String displayName, String formattedAddress, String websiteUri, Double rating, String priceLevel, Double latitude, Double longitude, String mapUrl) {
        this.displayName = displayName;
        this.formattedAddress = formattedAddress;
        this.websiteUri = websiteUri;
        this.rating = rating;
        this.priceLevel = priceLevel;
        this.latitude = latitude;
        this.longitude = longitude;
        this.mapUrl = mapUrl;
    }

    public String getDisplayName() {
        return displayName;
    }
    public String getFormattedAddress() {
        return formattedAddress;
    }
    public String getWebsiteUri() {
        return websiteUri;
    }
    public Double getRating() {
        return rating;
    }
    public String getPriceLevel() {
        return priceLevel;
    }
    public Double getLatitude() {
        return latitude;
    }
    public Double getLongitude() {
        return longitude;
    }
    public String getMapUrl() {
        return mapUrl;
    }
}