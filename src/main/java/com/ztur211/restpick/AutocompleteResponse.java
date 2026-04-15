package com.ztur211.restpick;

import java.util.List;

public class AutocompleteResponse {
    // Suggestions = ordered array of predictions in Google API response based on perceived relevance
    // Prediction = individual autocomplete suggestions in Google API response
    private List<Suggestion> suggestions;

    public List<Suggestion> getSuggestions() {
        return suggestions;
    }
    public void setSuggestions(List<Suggestion> suggestions) {
        this.suggestions = suggestions;
    }

    public static class Suggestion {
        private PlacePrediction placePrediction;

        public PlacePrediction getPlacePrediction() {
            return placePrediction;
        }
        public void setPlacePrediction(PlacePrediction placePrediction) {
            this.placePrediction = placePrediction;
        }
    }

    public static class PlacePrediction {
        private String placeId; // Places Autocomplete uses place_id and not name
        private StructuredFormat structuredFormat;

        public String getPlaceId() {
            return placeId;
        }
        public void setPlaceId(String placeId) {
            this.placeId = placeId;
        }

        public StructuredFormat getStructuredFormat() {
            return structuredFormat;
        }
        public void setStructuredFormat(StructuredFormat structuredFormat) {
            this.structuredFormat = structuredFormat;
        }
    }

    public static class StructuredFormat {
        private TextBlock mainText;
        private TextBlock secondaryText;

        public TextBlock getMainText() {
            return mainText;
        }
        public void setMainText(TextBlock mainText) {
            this.mainText = mainText;
        }

        public TextBlock getSecondaryText() {
            return secondaryText;
        }
        public void setSecondaryText(TextBlock secondaryText) {
            this.secondaryText = secondaryText;
        }
    }

    // Need TextBlock because text in Google API response is a nested object and not a plain string
    public static class TextBlock {
        private String text;

        public String getText() {
            return text;
        }
        public void setText(String text) {
            this.text = text;
        }
    }
}
