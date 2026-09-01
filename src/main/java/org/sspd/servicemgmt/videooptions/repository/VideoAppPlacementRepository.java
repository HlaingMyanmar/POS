package org.sspd.servicemgmt.videooptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.videooptions.model.VideoAppPlacement;
import org.sspd.servicemgmt.videooptions.model.VideoAppType;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoAppPlacementRepository extends JpaRepository<VideoAppPlacement, Integer> {

    @Query("""
            SELECT p FROM VideoAppPlacement p
            JOIN FETCH p.video v
            WHERE p.appType = :appType
            ORDER BY p.sortOrder ASC, p.id ASC
            """)
    List<VideoAppPlacement> findByAppTypeOrdered(@Param("appType") VideoAppType appType);

    @Query("""
            SELECT p FROM VideoAppPlacement p
            JOIN FETCH p.video v
            WHERE p.appType = :appType AND v.active = true AND p.active = true
            ORDER BY p.featured DESC, p.sortOrder ASC, v.createdAt DESC
            """)
    List<VideoAppPlacement> findActiveCatalog(@Param("appType") VideoAppType appType);

    Optional<VideoAppPlacement> findByVideoIdAndAppType(Integer videoId, VideoAppType appType);

    List<VideoAppPlacement> findByVideoId(Integer videoId);

    @Query("SELECT COALESCE(MAX(p.sortOrder), 0) FROM VideoAppPlacement p WHERE p.appType = :appType")
    int findMaxSortOrder(@Param("appType") VideoAppType appType);
}
