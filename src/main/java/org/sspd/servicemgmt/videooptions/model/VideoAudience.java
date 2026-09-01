package org.sspd.servicemgmt.videooptions.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Content targeting for mobile apps. Add a new value here when a new app
 * needs its own catalog; {@code BOTH} stays an explicit shared flag and is
 * not automatically expanded to every future audience.
 */
public enum VideoAudience {
    TECHNICIAN,
    CLIENT,
    BOTH;

    public static Set<VideoAudience> technicianCatalog() {
        return EnumSet.of(TECHNICIAN, BOTH);
    }

    public static Set<VideoAudience> clientCatalog() {
        return EnumSet.of(CLIENT, BOTH);
    }

    public static Set<VideoAudience> all() {
        return EnumSet.allOf(VideoAudience.class);
    }
}
