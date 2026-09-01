package org.sspd.servicemgmt.videooptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.videooptions.model.Video;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Integer> {

    @Query("SELECT DISTINCT v FROM Video v LEFT JOIN FETCH v.placements ORDER BY v.sortOrder ASC, v.title ASC")
    List<Video> findAllWithPlacements();

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.placements WHERE v.id = :id")
    Optional<Video> findWithPlacementsById(@Param("id") Integer id);
}
