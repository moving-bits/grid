package com.movingbits.grid.demo;

/**
 * demo data
 */
public record City(String city, String country, Integer population, Integer area) {

    public String getPopulationAsString() {
        return String.valueOf(population);
    }

    public String getAreaAsString() {
        return String.valueOf(area);
    }

}
