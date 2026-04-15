package com.ztur211.restpick;

import java.util.List;

// Model class representing a place returned by the Google Maps API
public class Place {
    private String name;
    private DisplayName displayName;
    private String formattedAddress;
    private String websiteUri;
    private Double rating;
    private Integer ratingCount;
    private String priceLevel;
    private Location location;
    private List<Photo> photos;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

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

    public Integer getRatingCount() {
        return ratingCount;
    }
    public void setRatingCount(Integer ratingCount) {
        this.ratingCount = ratingCount;
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

    public List<Photo> getPhotos() {
        return photos;
    }
    public void setPhotos(List<Photo> photos) {
        this.photos = photos;
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

    public static class Photo {
        private String name; // Photo reference
        private Integer widthPx;
        private Integer heightPx;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public Integer getWidthPx() {
            return widthPx;
        }
        public void setWidthPx(Integer widthPx) {
            this.widthPx = widthPx;
        }

        public Integer getHeightPx() {
            return heightPx;
        }
        public void setHeightPx(Integer heightPx) {
            this.heightPx = heightPx;
        }
    }
}