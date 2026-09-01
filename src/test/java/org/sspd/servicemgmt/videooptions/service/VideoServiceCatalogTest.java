package org.sspd.servicemgmt.videooptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.sspd.servicemgmt.videooptions.dto.VideoDTO;
import org.sspd.servicemgmt.videooptions.model.Video;
import org.sspd.servicemgmt.videooptions.model.VideoAppPlacement;
import org.sspd.servicemgmt.videooptions.model.VideoAppType;
import org.sspd.servicemgmt.videooptions.model.VideoAudience;
import org.sspd.servicemgmt.videooptions.repository.VideoAppPlacementRepository;
import org.sspd.servicemgmt.videooptions.repository.VideoRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoServiceCatalogTest {

    @Test
    void catalogUsesTechnicianPlacementOrderAndIgnoresClientOrder() {
        VideoRepository videos = mock(VideoRepository.class);
        VideoAppPlacementRepository placements = mock(VideoAppPlacementRepository.class);
        when(placements.findActiveCatalog(eq(VideoAppType.TECHNICIAN))).thenReturn(List.of(
                placement(video(10, "Company intro", VideoAudience.BOTH, true), VideoAppType.TECHNICIAN, 5, false),
                placement(video(1, "Laptop diagnosis", VideoAudience.TECHNICIAN, true), VideoAppType.TECHNICIAN, 1, true),
                placement(video(2, "Warranty information", VideoAudience.CLIENT, true), VideoAppType.TECHNICIAN, 2, false)
        ));
        when(placements.findActiveCatalog(eq(VideoAppType.CLIENT))).thenReturn(List.of(
                placement(video(10, "Company intro", VideoAudience.BOTH, true), VideoAppType.CLIENT, 1, true),
                placement(video(3, "How to request service", VideoAudience.CLIENT, true), VideoAppType.CLIENT, 2, false)
        ));

        VideoService service = new VideoService(videos, placements);
        List<VideoDTO> technicianVideos = service.findCatalog(auth("ROLE_TECHNICIAN"));
        List<VideoDTO> clientVideos = service.findCatalog(auth("CAN_ACCESS_VIDEO_CATALOG_CLIENT"));

        assertEquals(List.of("Company intro", "Laptop diagnosis"), titles(technicianVideos));
        assertEquals(List.of(5, 1), technicianVideos.stream().map(VideoDTO::getSortOrder).toList());
        assertTrue(technicianVideos.get(1).getFeatured());
        assertEquals(List.of("Company intro", "How to request service"), titles(clientVideos));
        assertEquals(1, clientVideos.get(0).getSortOrder());
    }

    @Test
    void clientCannotReadTechnicianMobileCatalog() {
        VideoService service = new VideoService(mock(VideoRepository.class), mock(VideoAppPlacementRepository.class));
        assertThrows(AccessDeniedException.class,
                () -> service.findMobileCatalog(auth("ROLE_CLIENT"), VideoAppType.TECHNICIAN));
        assertThrows(AccessDeniedException.class, () -> service.findCatalog(auth("ROLE_CASHIER")));
    }

    @Test
    void spliceKeepsUnfilteredVideosInPlace() {
        List<Integer> result = VideoService.spliceOrder(List.of(1, 2, 3, 4, 5), List.of(2, 4), List.of(4, 2));
        assertEquals(List.of(1, 4, 3, 2, 5), result);
    }

    private static List<String> titles(List<VideoDTO> videos) {
        return videos.stream().map(VideoDTO::getTitle).toList();
    }

    private static Video video(int id, String title, VideoAudience audience, boolean active) {
        return Video.builder()
                .id(id)
                .title(title)
                .provider("YOUTUBE")
                .providerVideoId("abcDEFghijk")
                .sourceUrl("https://youtu.be/abcDEFghijk")
                .targetAudience(audience)
                .sortOrder(0)
                .active(active)
                .createdAt(LocalDateTime.now().minusDays(id))
                .build();
    }

    private static VideoAppPlacement placement(Video video, VideoAppType appType, int sortOrder, boolean featured) {
        return VideoAppPlacement.builder()
                .id(video.getId() * 10)
                .video(video)
                .appType(appType)
                .sortOrder(sortOrder)
                .featured(featured)
                .active(true)
                .build();
    }

    private static Authentication auth(String authority) {
        return new UsernamePasswordAuthenticationToken(
                "user",
                "n/a",
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
