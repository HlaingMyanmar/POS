package org.sspd.servicemgmt.videooptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.videooptions.dto.VideoArrangementRequest;
import org.sspd.servicemgmt.videooptions.dto.VideoDTO;
import org.sspd.servicemgmt.videooptions.model.VideoAppType;
import org.sspd.servicemgmt.videooptions.model.VideoAudience;
import org.sspd.servicemgmt.videooptions.service.VideoService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/videos")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<VideoDTO>>> getAll(
            @RequestParam(required = false) VideoAudience audience,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Videos", service.findAll(audience, category, active)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_READ')")
    @GetMapping("/arrangement")
    public ResponseEntity<ApiResponse<List<VideoDTO>>> arrangement(
            @RequestParam VideoAppType appType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Video arrangement", service.findArrangement(appType, category, active)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_UPDATE')")
    @PutMapping("/arrangement/{appType}")
    public ResponseEntity<ApiResponse<List<VideoDTO>>> saveArrangement(
            @PathVariable VideoAppType appType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            @Valid @RequestBody VideoArrangementRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Video order saved", service.saveArrangement(appType, request, category, active)));
    }

    /**
     * Mobile catalog. App/audience is taken from the authenticated user,
     * never from a client-supplied query parameter.
     */
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<VideoDTO>>> catalog(Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Video catalog", service.findCatalog(authentication)));
    }

    @GetMapping("/mobile/technician")
    public ResponseEntity<ApiResponse<List<VideoDTO>>> technicianCatalog(Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Technician videos",
                service.findMobileCatalog(authentication, VideoAppType.TECHNICIAN)));
    }

    @GetMapping("/mobile/client")
    public ResponseEntity<ApiResponse<List<VideoDTO>>> clientCatalog(Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Client videos",
                service.findMobileCatalog(authentication, VideoAppType.CLIENT)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Video", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<VideoDTO>> create(@Valid @RequestBody VideoDTO dto) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Video created", service.create(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoDTO>> update(@PathVariable Integer id, @Valid @RequestBody VideoDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Video updated", service.update(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_VIDEO_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Video deleted", null));
    }
}
