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
        private String placeId;
        private TextBlock text;

        public String getPlaceId() {
            return placeId;
        }
        public void setPlaceId(String placeId) {
            this.placeId = placeId;
        }

        public TextBlock getText() {
            return text;
        }
        public void setText(TextBlock text) {
            this.text = text;
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
