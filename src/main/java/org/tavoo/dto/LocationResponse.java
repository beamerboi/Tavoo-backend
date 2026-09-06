package org.tavoo.dto;

import org.tavoo.entity.LocationType;

public record LocationResponse(
        Long id,
        String name,
        LocationType type
) {
}
