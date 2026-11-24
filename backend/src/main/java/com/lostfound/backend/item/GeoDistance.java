package com.lostfound.backend.item;

/** Distance helpers for the "near me" filter. */
public final class GeoDistance {

    private static final double EARTH_RADIUS_KM = 6371.0;
    /** Kilometres per degree of latitude (roughly constant everywhere). */
    public static final double KM_PER_DEGREE_LAT = 111.32;

    private GeoDistance() {
    }

    /** Great-circle distance between two points, in kilometres (Haversine formula). */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Kilometres per degree of longitude at this latitude (shrinks towards the poles). */
    public static double kmPerDegreeLng(double lat) {
        return KM_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat));
    }
}
