package tn.esprit.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "events")
@Document(indexName = "events")
public class Event {

    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient
    @org.springframework.data.annotation.Id
    private String esId;

    /**
     * ES-only fields used by the repository query.
     * We populate them from the persisted fields before indexing.
     */
    @Transient
    private String name;

    @Transient
    private String description;

    @Column(nullable = false)
    private String title;

    private LocalDate startDate;

    private LocalDate endDate;

    private String location;

    private Integer capacity;

    private Float price;

    @Column(nullable = false)
    private Long userId;

    @jakarta.persistence.Lob
    @Column(columnDefinition = "LONGTEXT")
    private String imageBase64;

}
