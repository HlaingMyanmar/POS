package org.sspd.servicemgmt.videooptions.model;

import java.util.List;
import java.util.Set;

/**
 * Concrete mobile app that can show a video. Distinct from {@link VideoAudience},
 * which may be BOTH. A BOTH video gets one placement per app.
 */
public enum VideoAppType {
    TECHNICIAN,
    CLIENT;

    public Set<VideoAudience> visibleAudiences() {
        return this == TECHNICIAN ? VideoAudience.technicianCatalog() : VideoAudience.clientCatalog();
    }

    public boolean visibleTo(VideoAudience audience) {
        return audience != null && visibleAudiences().contains(audience);
    }

    public static List<VideoAppType> forAudience(VideoAudience audience) {
        if (audience == VideoAudience.BOTH) {
            return List.of(TECHNICIAN, CLIENT);
        }
        if (audience == VideoAudience.CLIENT) {
            return List.of(CLIENT);
        }
        return List.of(TECHNICIAN);
    }
}
