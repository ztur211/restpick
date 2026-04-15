package com.ztur211.restpick;

import java.util.List;

// Model class representing a restaurant with relevant details for the frontend
public class Restaurant {
    private String name;
    private String displayName;
    private String formattedAddress;
    private String websiteUri;
    private Double rating;
    private Integer ratingCount;
    private String priceLevel;
    private Double latitude;
    private Double longitude;
    private String originAddress;
    private List<String> photos;
    
    public Restaurant( String name, String displayName, String formattedAddress, String websiteUri, Double rating, Integer ratingCount, String priceLevel, Double latitude, Double longitude, String originAddress, List<String> photos) {
        this.name = name;
        this.displayName = displayName;
        this.formattedAddress = formattedAddress;
        this.websiteUri = websiteUri;
        this.rating = rating;
        this.ratingCount = ratingCount;
        this.priceLevel = priceLevel;
        this.latitude = latitude;
        this.longitude = longitude;
        this.originAddress = originAddress;
        this.photos = photos;
    }

    public String getName() {
        return name;
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
    public Integer getRatingCount() {
        return ratingCount;
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
    public String getOriginAddress() {
        return originAddress;
    }
    public List<String> getPhotos() {
        return photos;
    }
}