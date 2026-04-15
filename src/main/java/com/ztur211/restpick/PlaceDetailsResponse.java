package com.ztur211.restpick;

import java.util.List;

public class PlaceDetailsResponse {

    private DisplayName displayName;
    private String formattedAddress;
    private String websiteUri;
    private Double rating;
    private Integer userRatingCount;
    private String priceLevel;
    private Location location;
    private List<Photo> photos;

    public DisplayName getDisplayName() {
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

    public Integer getUserRatingCount() {
        return userRatingCount;
    }

    public String getPriceLevel() {
        return priceLevel;
    }

    public Location getLocation() {
        return location;
    }

    public List<Photo> getPhotos() {
        return photos;
    }

    public static class DisplayName {
        private String text;

        public String getText() {
            return text;
        }
    }

    public static class Location {
        private double latitude;
        private double longitude;

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }

    public static class Photo {
        private String name;

        public String getName() {
            return name;
        }
    }
}
