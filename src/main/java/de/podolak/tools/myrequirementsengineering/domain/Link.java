package de.podolak.tools.myrequirementsengineering.domain;

import jakarta.persistence.*;

import java.util.Objects;

/**
 * Link entity for traceability between requirements (e.g. "implements", "depends on").
 * Not yet used in the initial UI but part of the data model.
 */
@Entity
@Table(name = "link")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long source;

    @Column(nullable = false)
    private Long target;

    public Link() {
    }

    public Link(Long source, Long target) {
        this.source = source;
        this.target = target;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSource() {
        return source;
    }

    public void setSource(Long source) {
        this.source = source;
    }

    public Long getTarget() {
        return target;
    }

    public void setTarget(Long target) {
        this.target = target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Link link = (Link) o;
        return Objects.equals(id, link.id) ||
                (Objects.equals(source, link.source) && Objects.equals(target, link.target));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, target);
    }

    @Override
    public String toString() {
        return "Link{" +
                "id=" + id +
                ", source=" + source +
                ", target=" + target +
                '}';
    }
}
