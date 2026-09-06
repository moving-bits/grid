package com.movingbits.grid;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A plain record for the tests.
 */
record Person(String city, String name, int amount) {

    static List<Person> data() {
        return new ArrayList<>(Arrays.asList(
                new Person("New York", "Schulz", 300),
                // The umlaut is deliberate: it is what proves the language-aware collation.
                new Person("Rio", "Ärmel", 1000),
                new Person("New York", "Adam", 90),
                new Person("Rio", "Zander", 20)));
    }

    /**
     * Grid with the columns City, Name and Amount; Amount with a comparator of its own.
     */
    static MemGrid<Person> grid(List<Person> people) {
        return new MemGrid<>(people)
                .column(new MemColumn<Person>("City", item -> item.city).name("city"))
                .column(new MemColumn<Person>("Name", item -> item.name).name("name"))
                // Without a comparator of its own "1000" would sort before "300".
                .column(new MemColumn<Person>("Amount", item -> String.valueOf(item.amount))
                        .name("amount")
                        .comparator(Comparator.comparingInt(a -> a.amount)));
    }

    @NonNull
    @Override
    public String toString() {
        return city + "/" + name + "/" + amount;
    }
}
