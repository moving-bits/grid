package com.movingbits.grid.demo;

/**
 * demo data
 */
public class Mountain {

    private final String mountain;
    private final Integer height;
    private final String lat;
    private final String lon;
    private final Integer first;
    private final String country;

    public Mountain(final String mountain, final Integer height, final String lat, final String lon, final Integer first, final String country) {
        this.mountain = mountain;
        this.height = height;
        this.lat = lat;
        this.lon = lon;
        this.first = first;
        this.country = country;
    }

    public String getMountain() {
        return mountain;
    }

    public String getHeightAsString() {
        return String.valueOf(height);
    }

    public String getLat() {
        return lat;
    }

    public String getLon() {
        return lon;
    }

    public String getFirstAsString() {
        return String.valueOf(first);
    }

    public String getCountry() {
        return country;
    }

}
