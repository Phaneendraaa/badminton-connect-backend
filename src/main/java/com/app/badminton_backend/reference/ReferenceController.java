package com.app.badminton_backend.reference;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves server-defined reference data that clients need for dropdowns/pickers.
 * Using a constant list (not a DB table) for v1 — adding a city only requires
 * updating this list, which is a single-line change with no schema migration.
 */
@RestController
@RequestMapping("/reference")
public class ReferenceController {

    /**
     * Curated list of major Indian cities for match-post city selection.
     * "Other" is always the final sentinel value — when selected, the frontend
     * must show a free-text input for cityOther.
     *
     * To add a city: add it to CITIES below (alphabetically sorted by region,
     * "Other" always last). No database migration needed.
     */
    private static final List<String> CITIES = List.of(
            // Major metros & state capitals (alphabetical)
            "Agartala",
            "Agra",
            "Ahmedabad",
            "Aizawl",
            "Amaravati",
            "Amritsar",
            "Bengaluru",
            "Bhopal",
            "Bhubaneswar",
            "Chandigarh",
            "Chennai",
            "Coimbatore",
            "Dehradun",
            "Delhi",
            "Dispur",
            "Gangtok",
            "Gandhinagar",
            "Hyderabad",
            "Imphal",
            "Indore",
            "Itanagar",
            "Jaipur",
            "Jammu",
            "Kanpur",
            "Kochi",
            "Kohima",
            "Kolkata",
            "Lucknow",
            "Mumbai",
            "Nagpur",
            "Panaji",
            "Patna",
            "Puducherry",
            "Pune",
            "Raipur",
            "Ranchi",
            "Shillong",
            "Shimla",
            "Srinagar",
            "Surat",
            "Thane",
            "Thiruvananthapuram",
            "Visakhapatnam",
            "Other"
    );

    /**
     * Returns the ordered list of supported cities plus "Other".
     * Clients should cache this locally — it changes infrequently.
     * Response is cached server-side via Spring Cache.
     */
    @GetMapping("/cities")
    @Cacheable("cities")
    public ResponseEntity<List<String>> getCities() {
        return ResponseEntity.ok(CITIES);
    }

    /**
     * Utility method so services can validate submitted city values
     * without duplicating the list. "Other" is always valid.
     */
    public static boolean isValidCity(String city) {
        return city != null && CITIES.contains(city);
    }
}
