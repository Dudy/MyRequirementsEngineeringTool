package de.podolak.tools.myrequirementsengineering.repository;

import de.podolak.tools.myrequirementsengineering.domain.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, Long> {

    List<Link> findBySource(Long source);

    List<Link> findByTarget(Long target);

    Optional<Link> findBySourceAndTarget(Long source, Long target);
}
