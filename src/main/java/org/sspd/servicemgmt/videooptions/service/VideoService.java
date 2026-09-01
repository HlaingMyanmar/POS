package org.sspd.servicemgmt.videooptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.videooptions.dto.VideoArrangementItemDTO;
import org.sspd.servicemgmt.videooptions.dto.VideoArrangementRequest;
import org.sspd.servicemgmt.videooptions.dto.VideoDTO;
import org.sspd.servicemgmt.videooptions.dto.VideoPlacementDTO;
import org.sspd.servicemgmt.videooptions.model.Video;
import org.sspd.servicemgmt.videooptions.model.VideoAppPlacement;
import org.sspd.servicemgmt.videooptions.model.VideoAppType;
import org.sspd.servicemgmt.videooptions.model.VideoAudience;
import org.sspd.servicemgmt.videooptions.model.VideoProvider;
import org.sspd.servicemgmt.videooptions.repository.VideoAppPlacementRepository;
import org.sspd.servicemgmt.videooptions.repository.VideoRepository;
import org.sspd.servicemgmt.videooptions.support.VideoAudienceResolver;
import org.sspd.servicemgmt.videooptions.support.YouTubeUrlParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository repository;
    private final VideoAppPlacementRepository placementRepository;

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_READ')")
    @Transactional(readOnly = true)
    public List<VideoDTO> findAll(VideoAudience audience, String category, Boolean active) {
        return repository.findAllWithPlacements().stream()
                .filter(video -> audience == null || video.getTargetAudience() == audience)
                .filter(video -> active == null || active.equals(video.getActive()))
                .filter(video -> categoryMatches(video.getCategory(), category))
                .map(this::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_READ')")
    @Transactional(readOnly = true)
    public VideoDTO findById(Integer id) {
        return toDto(require(id));
    }

    @Transactional(readOnly = true)
    public List<VideoDTO> findCatalog(Authentication authentication) {
        return findCatalogForApp(VideoAudienceResolver.catalogApp(authentication));
    }

    @Transactional(readOnly = true)
    public List<VideoDTO> findMobileCatalog(Authentication authentication, VideoAppType appType) {
        VideoAudienceResolver.requireCatalogApp(authentication, appType);
        return findCatalogForApp(appType);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_READ')")
    @Transactional(readOnly = true)
    public List<VideoDTO> findArrangement(VideoAppType appType, String category, Boolean active) {
        return placementRepository.findByAppTypeOrdered(appType).stream()
                .filter(placement -> appType.visibleTo(placement.getVideo().getTargetAudience()))
                .filter(placement -> matchesArrangementFilter(placement.getVideo(), category, active))
                .sorted(arrangementComparator())
                .map(this::toArrangementDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_CREATE')")
    @Transactional
    public VideoDTO create(VideoDTO dto) {
        Video entity = new Video();
        applyMetadata(dto, entity);
        entity.setSortOrder(0);
        Video saved = repository.save(entity);
        for (VideoAppType appType : VideoAppType.forAudience(saved.getTargetAudience())) {
            appendPlacement(saved, appType);
        }
        return toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_UPDATE')")
    @Transactional
    public VideoDTO update(Integer id, VideoDTO dto) {
        Video entity = require(id);
        VideoAudience previous = entity.getTargetAudience();
        applyMetadata(dto, entity);
        repository.save(entity);
        if (previous != entity.getTargetAudience()) {
            syncPlacements(entity);
        }
        return toDto(entity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_DELETE')")
    @Transactional
    public void delete(Integer id) {
        Video entity = require(id);
        Set<VideoAppType> apps = placementsOf(entity).stream()
                .map(VideoAppPlacement::getAppType)
                .collect(Collectors.toCollection(HashSet::new));
        repository.delete(entity);
        repository.flush();
        apps.forEach(this::normalizeAppOrder);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_UPDATE')")
    @Transactional
    public List<VideoDTO> saveArrangement(VideoAppType appType, VideoArrangementRequest request, String category, Boolean active) {
        List<VideoArrangementItemDTO> items = request.getItems();
        List<VideoAppPlacement> all = placementRepository.findByAppTypeOrdered(appType);
        Map<Integer, VideoAppPlacement> byVideoId = all.stream()
                .collect(Collectors.toMap(placement -> placement.getVideo().getId(), Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<Integer> submittedIds = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (VideoArrangementItemDTO item : items) {
            Integer videoId = item.getVideoId();
            VideoAppPlacement placement = byVideoId.get(videoId);
            if (placement == null) {
                throw new IllegalArgumentException("Video " + videoId + " is not placed in " + appType);
            }
            if (!appType.visibleTo(placement.getVideo().getTargetAudience())) {
                throw new IllegalArgumentException("Video " + videoId + " is not visible in " + appType);
            }
            if (!seen.add(videoId)) {
                throw new IllegalArgumentException("Duplicate video in arrangement: " + videoId);
            }
            submittedIds.add(videoId);
            placement.setFeatured(Boolean.TRUE.equals(item.getFeatured()));
        }

        List<Integer> filteredIds = all.stream()
                .filter(placement -> matchesArrangementFilter(placement.getVideo(), category, active))
                .map(placement -> placement.getVideo().getId())
                .toList();
        if (!seen.equals(new HashSet<>(filteredIds))) {
            throw new IllegalArgumentException("Arrangement list must include exactly the filtered videos for this app");
        }

        List<Integer> newOrder = spliceOrder(
                all.stream().map(placement -> placement.getVideo().getId()).toList(),
                filteredIds,
                submittedIds
        );
        int sortOrder = 1;
        for (Integer videoId : newOrder) {
            VideoAppPlacement placement = byVideoId.get(videoId);
            if (placement != null) {
                placement.setSortOrder(sortOrder++);
            }
        }
        placementRepository.saveAll(all);
        return findArrangement(appType, category, active);
    }

    private List<VideoDTO> findCatalogForApp(VideoAppType appType) {
        return placementRepository.findActiveCatalog(appType).stream()
                .filter(placement -> appType.visibleTo(placement.getVideo().getTargetAudience()))
                .map(this::toArrangementDto)
                .toList();
    }

    private void syncPlacements(Video video) {
        Set<VideoAppType> wanted = new HashSet<>(VideoAppType.forAudience(video.getTargetAudience()));
        List<VideoAppPlacement> existing = new ArrayList<>(placementsOf(video));
        Set<VideoAppType> removed = new HashSet<>();
        for (VideoAppPlacement placement : existing) {
            if (!wanted.contains(placement.getAppType())) {
                placementsOf(video).remove(placement);
                removed.add(placement.getAppType());
            }
        }
        for (VideoAppType appType : wanted) {
            boolean present = placementsOf(video).stream().anyMatch(placement -> placement.getAppType() == appType);
            if (!present) {
                appendPlacement(video, appType);
            }
        }
        repository.save(video);
        repository.flush();
        removed.forEach(this::normalizeAppOrder);
    }

    private void appendPlacement(Video video, VideoAppType appType) {
        boolean exists = placementsOf(video).stream().anyMatch(placement -> placement.getAppType() == appType);
        if (exists) {
            return;
        }
        VideoAppPlacement placement = VideoAppPlacement.builder()
                .video(video)
                .appType(appType)
                .sortOrder(placementRepository.findMaxSortOrder(appType) + 1)
                .featured(Boolean.FALSE)
                .active(Boolean.TRUE)
                .build();
        placementsOf(video).add(placement);
    }

    private static List<VideoAppPlacement> placementsOf(Video video) {
        if (video.getPlacements() == null) {
            video.setPlacements(new ArrayList<>());
        }
        return video.getPlacements();
    }

    private void normalizeAppOrder(VideoAppType appType) {
        List<VideoAppPlacement> placements = placementRepository.findByAppTypeOrdered(appType);
        int sortOrder = 1;
        for (VideoAppPlacement placement : placements) {
            placement.setSortOrder(sortOrder++);
        }
        placementRepository.saveAll(placements);
    }

    private Video require(Integer id) {
        return repository.findWithPlacementsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + id));
    }

    private void applyMetadata(VideoDTO dto, Video entity) {
        String rawUrl = firstNonBlank(dto.getYoutubeUrl(), dto.getSourceUrl());
        String videoId = YouTubeUrlParser.parseVideoId(rawUrl);
        entity.setTitle(dto.getTitle().trim());
        entity.setDescription(blankToNull(dto.getDescription()));
        entity.setProvider(VideoProvider.YOUTUBE.name());
        entity.setProviderVideoId(videoId);
        entity.setSourceUrl(YouTubeUrlParser.watchUrl(videoId));
        entity.setThumbnailUrl(YouTubeUrlParser.thumbnailUrl(videoId));
        entity.setCategory(blankToNull(dto.getCategory()));
        entity.setTargetAudience(dto.getTargetAudience());
        entity.setActive(dto.getActive() == null || dto.getActive());
    }

    private VideoDTO toDto(Video entity) {
        VideoDTO dto = baseDto(entity);
        List<VideoPlacementDTO> placements = placementsOf(entity).stream()
                .sorted(Comparator.comparing(VideoAppPlacement::getAppType))
                .map(VideoService::toPlacementDto)
                .toList();
        dto.setPlacements(placements);
        return dto;
    }

    private VideoDTO toArrangementDto(VideoAppPlacement placement) {
        VideoDTO dto = baseDto(placement.getVideo());
        dto.setSortOrder(placement.getSortOrder());
        dto.setFeatured(Boolean.TRUE.equals(placement.getFeatured()));
        dto.setPlacements(List.of(toPlacementDto(placement)));
        return dto;
    }

    private VideoDTO baseDto(Video entity) {
        VideoDTO dto = new VideoDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setProvider(entity.getProvider());
        dto.setProviderVideoId(entity.getProviderVideoId());
        dto.setSourceUrl(entity.getSourceUrl());
        dto.setYoutubeUrl(entity.getSourceUrl());
        dto.setThumbnailUrl(entity.getThumbnailUrl());
        dto.setCategory(entity.getCategory());
        dto.setTargetAudience(entity.getTargetAudience());
        dto.setActive(entity.getActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private static VideoPlacementDTO toPlacementDto(VideoAppPlacement placement) {
        VideoPlacementDTO dto = new VideoPlacementDTO();
        dto.setAppType(placement.getAppType());
        dto.setSortOrder(placement.getSortOrder());
        dto.setFeatured(placement.getFeatured());
        dto.setActive(placement.getActive());
        return dto;
    }

    private static Comparator<VideoAppPlacement> arrangementComparator() {
        return Comparator
                .comparing((VideoAppPlacement placement) -> !Boolean.TRUE.equals(placement.getFeatured()))
                .thenComparing(VideoAppPlacement::getSortOrder)
                .thenComparing((VideoAppPlacement placement) -> placement.getVideo().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    static List<Integer> spliceOrder(List<Integer> currentOrder, List<Integer> filteredIds, List<Integer> submittedIds) {
        Set<Integer> filtered = new HashSet<>(filteredIds);
        ArrayDeque<Integer> next = new ArrayDeque<>(submittedIds);
        List<Integer> result = new ArrayList<>();
        for (Integer videoId : currentOrder) {
            if (filtered.contains(videoId)) {
                Integer replacement = next.poll();
                result.add(replacement != null ? replacement : videoId);
            } else {
                result.add(videoId);
            }
        }
        result.addAll(next);
        return result;
    }

    private static boolean matchesArrangementFilter(Video video, String category, Boolean active) {
        return (active == null || active.equals(video.getActive()))
                && categoryMatches(video.getCategory(), category);
    }

    private static boolean categoryMatches(String value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
