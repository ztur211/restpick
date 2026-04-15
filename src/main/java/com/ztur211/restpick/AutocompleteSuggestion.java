package com.ztur211.restpick;

public class AutocompleteSuggestion {
    private String mainText;
    private String secondaryText;
    private String placeId;

    public AutocompleteSuggestion(String mainText, String secondaryText, String placeId) {
        this.mainText = mainText;
        this.secondaryText = secondaryText;
        this.placeId = placeId;
    }

    public String getMainText() {
        return mainText;
    }
    public String getSecondaryText() {
        return secondaryText;
    }
    public String getPlaceId() {
        return placeId;
    }
}
