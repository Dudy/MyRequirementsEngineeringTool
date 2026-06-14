package de.podolak.tools.myrequirementsengineering.repository;

import de.podolak.tools.myrequirementsengineering.domain.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequirementRepository extends JpaRepository<Requirement, Long> {

    List<Requirement> findByProjectIdOrderByIdAsc(Long projectId);

    List<Requirement> findByProjectIdAndParentId(Long projectId, Long parentId);

    List<Requirement> findByParentIdOrderByTitleAsc(Long parentId);

    @Query("select coalesce(max(r.projectId), 0L) from Requirement r")
    Long findMaxProjectId();

    @Query("select r.identifier from Requirement r where r.projectId = :projectId")
    List<String> findIdentifiersByProjectId(@Param("projectId") Long projectId);

    Optional<Requirement> findByProjectIdAndParentIdAndIdentifier(Long projectId, Long parentId, String identifier);

    List<Requirement> findByProjectIdAndParentIdOrderByTitleAsc(Long projectId, Long parentId);
}
