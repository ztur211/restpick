package com.ztur211.restpick;

// Model class representing a place returned by the Google Maps API
public class Place {
    private DisplayName displayName;
    private String formattedAddress;
    private String websiteUri;
    private Double rating;
    private String priceLevel;
    private Location location;

    public DisplayName getDisplayName() {
        return displayName;
    }
    public void setDisplayName(DisplayName displayName) {
        this.displayName = displayName;
    }

    public String getFormattedAddress() {
        return formattedAddress;
    }
    public void setFormattedAddress(String formattedAddress) {
        this.formattedAddress = formattedAddress;
    }

    public String getWebsiteUri() {
        return websiteUri;
    }
    public void setWebsiteUri(String websiteUri) {
        this.websiteUri = websiteUri;
    }

    public Double getRating() {
        return rating;
    }
    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getPriceLevel() {
        return priceLevel;
    }
    public void setPriceLevel(String priceLevel) {
        this.priceLevel = priceLevel;
    }

    public Location getLocation() {
        return location;
    }
    public void setLocation(Location location) {
        this.location = location;
    }

    public static class DisplayName {
        private String text;

        public String getText() {
            return text;
        }
        public void setText(String text) {
            this.text = text;
        }
    }

    public static class Location {
        private Double latitude;
        private Double longitude;

        public Double getLatitude() {
            return latitude;
        }
        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }
        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }
    }
}