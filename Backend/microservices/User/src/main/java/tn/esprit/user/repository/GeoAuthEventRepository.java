package tn.esprit.user.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.user.entity.GeoAuthEvent;
import tn.esprit.user.entity.GeoEventType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GeoAuthEventRepository extends JpaRepository<GeoAuthEvent, Long> {

    Optional<GeoAuthEvent> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<GeoAuthEvent> findTopByUserIdAndEventTypeOrderByCreatedAtDesc(Long userId, GeoEventType eventType);

    Optional<GeoAuthEvent> findTopByUserIdAndImpossibleTravelIsTrueOrderByCreatedAtDesc(Long userId);

    List<GeoAuthEvent> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    List<GeoAuthEvent> findByImpossibleTravelIsTrueAndCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    /** Historique admin : derniers événements géo pour un utilisateur (tri par date décroissante). */
    List<GeoAuthEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
            select function('date', e.createdAt) as d, count(e.id) as c
            from GeoAuthEvent e
            where e.eventType = :type and e.createdAt >= :since
            group by function('date', e.createdAt)
            order by d
            """)
    List<Object[]> countByDaySince(@Param("type") GeoEventType type, @Param("since") LocalDateTime since);

    @Query("""
            select e.userId as uid, count(e.id) as c
            from GeoAuthEvent e
            where e.eventType = :type and e.createdAt >= :since
            group by e.userId
            order by c desc
            """)
    List<Object[]> topUsersByEventsSince(@Param("type") GeoEventType type,
                                        @Param("since") LocalDateTime since);

    @Query("""
            select count(e.id)
            from GeoAuthEvent e
            where e.impossibleTravel = true and e.createdAt >= :since
            """)
    long countImpossibleTravelSince(@Param("since") LocalDateTime since);

    /** Logins sur la fenêtre [sinceTime, +∞) — JPQL explicite (paramètre évite le mot réservé SQL « since »). */
    @Query(
            "select count(e) from GeoAuthEvent e "
                    + "where e.userId = :userId and e.eventType = :eventType and e.createdAt >= :sinceTime")
    Long countLoginsSince(
            @Param("userId") Long userId,
            @Param("eventType") GeoEventType eventType,
            @Param("sinceTime") LocalDateTime sinceTime);
}
