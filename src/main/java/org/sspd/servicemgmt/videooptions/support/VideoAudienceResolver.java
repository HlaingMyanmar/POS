package org.sspd.servicemgmt.videooptions.support;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.sspd.servicemgmt.videooptions.model.VideoAppType;
import org.sspd.servicemgmt.videooptions.model.VideoAudience;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps the authenticated principal to video audiences. Query parameters are
 * never used here — mobile apps cannot widen access by sending audience=.
 */
public final class VideoAudienceResolver {

    public static final String VIDEO_READ = "CAN_ACCESS_VIDEO_READ";
    public static final String CATALOG_TECHNICIAN = "CAN_ACCESS_VIDEO_CATALOG_TECHNICIAN";
    public static final String CATALOG_CLIENT = "CAN_ACCESS_VIDEO_CATALOG_CLIENT";

    private VideoAudienceResolver() {
    }

    public static Set<VideoAudience> catalogAudiences(Authentication authentication) {
        Set<String> authorities = authorities(authentication);
        if (authorities.isEmpty()) {
            throw new AccessDeniedException("Video catalog ကြည့်ရန် login လုပ်ပါ");
        }
        if (isAdmin(authorities)) {
            return VideoAudience.all();
        }

        Set<VideoAudience> allowed = EnumSet.noneOf(VideoAudience.class);
        if (isTechnician(authorities)) {
            allowed.addAll(VideoAudience.technicianCatalog());
        }
        if (isClient(authorities)) {
            allowed.addAll(VideoAudience.clientCatalog());
        }
        if (allowed.isEmpty()) {
            throw new AccessDeniedException("Video catalog ကြည့်ရန် ခွင့်ပြုချက်မရှိပါ");
        }
        return Collections.unmodifiableSet(allowed);
    }

    /**
     * Picks the single app catalog for a mobile caller. Query parameters are ignored.
     * Technician wins if the user is both technician and client.
     */
    public static VideoAppType catalogApp(Authentication authentication) {
        Set<String> authorities = authorities(authentication);
        if (authorities.isEmpty()) {
            throw new AccessDeniedException("Video catalog ကြည့်ရန် login လုပ်ပါ");
        }
        boolean technician = isTechnician(authorities);
        boolean client = isClient(authorities);
        if (technician && !client) {
            return VideoAppType.TECHNICIAN;
        }
        if (client && !technician) {
            return VideoAppType.CLIENT;
        }
        if (technician) {
            return VideoAppType.TECHNICIAN;
        }
        if (client) {
            return VideoAppType.CLIENT;
        }
        throw new AccessDeniedException("Video catalog ကြည့်ရန် ခွင့်ပြုချက်မရှိပါ");
    }

    public static void requireCatalogApp(Authentication authentication, VideoAppType appType) {
        Set<String> authorities = authorities(authentication);
        if (authorities.isEmpty()) {
            throw new AccessDeniedException("Video catalog ကြည့်ရန် login လုပ်ပါ");
        }
        if (isAdmin(authorities)) {
            return;
        }
        if (appType == VideoAppType.TECHNICIAN && isTechnician(authorities)) {
            return;
        }
        if (appType == VideoAppType.CLIENT && isClient(authorities)) {
            return;
        }
        throw new AccessDeniedException("ဤ app ၏ video catalog ကြည့်ရန် ခွင့်ပြုချက်မရှိပါ");
    }

    public static boolean isAdmin(Set<String> authorities) {
        return contains(authorities, VIDEO_READ)
                || contains(authorities, "ROLE_ADMINISTRATOR")
                || contains(authorities, "ADMINISTRATOR")
                || contains(authorities, "ROLE_ADMIN")
                || contains(authorities, "ADMIN");
    }

    public static boolean isTechnician(Set<String> authorities) {
        return contains(authorities, CATALOG_TECHNICIAN)
                || contains(authorities, "ROLE_TECHNICIAN")
                || contains(authorities, "TECHNICIAN");
    }

    public static boolean isClient(Set<String> authorities) {
        return contains(authorities, CATALOG_CLIENT)
                || contains(authorities, "ROLE_CLIENT")
                || contains(authorities, "CLIENT");
    }

    public static Set<String> authorities(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static boolean contains(Set<String> authorities, String value) {
        return authorities.contains(value.toUpperCase(Locale.ROOT));
    }
}
