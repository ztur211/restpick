package com.ztur211.restpick;

// Model class representing a restaurant with relevant details for the frontend
public class Restaurant {
    private String displayName;
    private String formattedAddress;
    private String websiteUri;
    private Double rating;
    private String priceLevel;

    public Restaurant(String displayName, String formattedAddress, String websiteUri, Double rating, String priceLevel) {
        this.displayName = displayName;
        this.formattedAddress = formattedAddress;
        this.websiteUri = websiteUri;
        this.rating = rating;
        this.priceLevel = priceLevel;
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
}