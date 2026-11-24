package com.lostfound.backend.item;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

/** Builds the WHERE clause for item search, adding only the filters that were actually given. */
final class ItemSpecifications {

    private ItemSpecifications() {
    }

    static Specification<Item> matching(ItemSearchCriteria c) {
        return (root, query, builder) -> {
            HibernateCriteriaBuilder cb = (HibernateCriteriaBuilder) builder;
            List<Predicate> where = new ArrayList<>();

            if (c.type() != null) where.add(cb.equal(root.get("type"), c.type()));
            if (c.status() != null) where.add(cb.equal(root.get("status"), c.status()));
            if (c.categoryId() != null) where.add(cb.equal(root.get("category").get("id"), c.categoryId()));
            if (c.from() != null) where.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("eventDate"), c.from()));
            if (c.to() != null) where.add(cb.lessThanOrEqualTo(root.<LocalDate>get("eventDate"), c.to()));

            String keyword = c.keyword();
            if (keyword != null) {
                String pattern = "%" + escapeLike(keyword) + "%";
                where.add(cb.or(
                        cb.ilike(root.<String>get("title"), pattern, '\\'),
                        cb.ilike(root.<String>get("description"), pattern, '\\'),
                        cb.ilike(root.<String>get("locationText"), pattern, '\\')));
            }

            if (c.hasLocation()) {
                where.addAll(withinRadius(cb, root.<Double>get("lat"), root.<Double>get("lng"), c.lat(), c.lng(), c.radiusKm()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /**
     * A box around the point (uses the lat/lng index), then a flat-earth distance check inside it.
     * Over a few kilometres the flat approximation is accurate to well under 1%.
     */
    private static List<Predicate> withinRadius(HibernateCriteriaBuilder cb, Expression<Double> lat, Expression<Double> lng,
                                                double centerLat, double centerLng, double radiusKm) {
        double kmPerLng = GeoDistance.kmPerDegreeLng(centerLat);
        double dLat = radiusKm / GeoDistance.KM_PER_DEGREE_LAT;
        double dLng = radiusKm / kmPerLng;

        Expression<Double> dyKm = cb.prod(cb.diff(lat, centerLat), GeoDistance.KM_PER_DEGREE_LAT);
        Expression<Double> dxKm = cb.prod(cb.diff(lng, centerLng), kmPerLng);
        Expression<Double> distanceSquared = cb.sum(cb.prod(dxKm, dxKm), cb.prod(dyKm, dyKm));

        return List.of(
                cb.between(lat, centerLat - dLat, centerLat + dLat),
                cb.between(lng, centerLng - dLng, centerLng + dLng),
                cb.le(distanceSquared, radiusKm * radiusKm));
    }

    /** Makes % and _ in the user's text match literally instead of acting as wildcards. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
