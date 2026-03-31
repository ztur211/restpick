package com.ztur211.restpick;

import java.util.List;

// This class represents the structure of the response from the Places API, which contains a list of Place objects
public class PlacesResponse {
    private List<Place> places;

    public List<Place> getPlaces() {
        return places;
    }

    public void setPlaces(List<Place> places) {
        this.places = places;
    }
}
