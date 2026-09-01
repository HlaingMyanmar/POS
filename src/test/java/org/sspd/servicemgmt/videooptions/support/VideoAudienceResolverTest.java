package org.sspd.servicemgmt.videooptions.support;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.sspd.servicemgmt.videooptions.model.VideoAudience;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoAudienceResolverTest {

    @Test
    void technicianSeesTechnicianAndBoth() {
        Set<VideoAudience> allowed = VideoAudienceResolver.catalogAudiences(auth("ROLE_TECHNICIAN"));
        assertEquals(VideoAudience.technicianCatalog(), allowed);
    }

    @Test
    void technicianCatalogPermissionIsEnoughWithoutRoleName() {
        Set<VideoAudience> allowed = VideoAudienceResolver.catalogAudiences(
                auth(VideoAudienceResolver.CATALOG_TECHNICIAN));
        assertEquals(VideoAudience.technicianCatalog(), allowed);
    }

    @Test
    void clientSeesClientAndBoth() {
        Set<VideoAudience> allowed = VideoAudienceResolver.catalogAudiences(
                auth(VideoAudienceResolver.CATALOG_CLIENT, "ROLE_CLIENT"));
        assertEquals(VideoAudience.clientCatalog(), allowed);
    }

    @Test
    void adminSeesAllAudiences() {
        Set<VideoAudience> allowed = VideoAudienceResolver.catalogAudiences(
                auth("ROLE_ADMIN", VideoAudienceResolver.VIDEO_READ));
        assertEquals(VideoAudience.all(), allowed);
    }

    @Test
    void clientCannotWidenAccessByClaimingTechnician() {
        Set<VideoAudience> allowed = VideoAudienceResolver.catalogAudiences(auth("ROLE_CLIENT"));
        assertTrue(allowed.contains(VideoAudience.CLIENT));
        assertTrue(allowed.contains(VideoAudience.BOTH));
        assertTrue(allowed.stream().noneMatch(audience -> audience == VideoAudience.TECHNICIAN));
    }

    @Test
    void unknownRoleIsDenied() {
        assertThrows(AccessDeniedException.class,
                () -> VideoAudienceResolver.catalogAudiences(auth("ROLE_CASHIER")));
        assertThrows(AccessDeniedException.class,
                () -> VideoAudienceResolver.catalogAudiences(null));
    }

    @Test
    void catalogAppIgnoresAdminOnlyUsersWithoutAppRole() {
        assertEquals(org.sspd.servicemgmt.videooptions.model.VideoAppType.TECHNICIAN,
                VideoAudienceResolver.catalogApp(auth("ROLE_TECHNICIAN")));
        assertEquals(org.sspd.servicemgmt.videooptions.model.VideoAppType.CLIENT,
                VideoAudienceResolver.catalogApp(auth("CAN_ACCESS_VIDEO_CATALOG_CLIENT")));
        assertThrows(AccessDeniedException.class, () -> VideoAudienceResolver.catalogApp(auth("ROLE_ADMIN")));
    }

    @Test
    void requireCatalogAppBlocksCrossAppAccess() {
        VideoAudienceResolver.requireCatalogApp(auth("ROLE_ADMIN"), org.sspd.servicemgmt.videooptions.model.VideoAppType.TECHNICIAN);
        assertThrows(AccessDeniedException.class, () ->
                VideoAudienceResolver.requireCatalogApp(auth("ROLE_CLIENT"), org.sspd.servicemgmt.videooptions.model.VideoAppType.TECHNICIAN));
    }

    private static Authentication auth(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                "user",
                "n/a",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }
}
