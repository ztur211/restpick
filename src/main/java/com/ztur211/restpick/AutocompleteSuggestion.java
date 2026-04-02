package com.ztur211.restpick;

public class AutocompleteSuggestion {
    private String placeId;
    private String address;

    public AutocompleteSuggestion(String placeId, String address) {
        this.placeId = placeId;
        this.address = address;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getAddress() {
        return address;
    }

}
