package tn.esprit.training.mapper;

import tn.esprit.training.dto.AvisDTO;
import tn.esprit.training.dto.TrainingContentDTO;
import tn.esprit.training.dto.TrainingRequestDTO;
import tn.esprit.training.dto.TrainingResponseDTO;
import tn.esprit.training.entity.Avis;
import tn.esprit.training.entity.Training;
import tn.esprit.training.entity.TrainingContent;

import java.util.List;
import java.util.stream.Collectors;

public class TrainingMapper {
    public static TrainingResponseDTO toDto(Training entity) {
        TrainingResponseDTO dto = new TrainingResponseDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        dto.setLevel(entity.getLevel());
        dto.setPrice(entity.getPrice());
        dto.setThumbnailUrl(entity.getThumbnailUrl());
        dto.setLanguage(entity.getLanguage());
        dto.setStatus(entity.getStatus());
        List<TrainingContentDTO> contentDTOs = entity.getContents()
                .stream()
                .map(TrainingMapper::toDto)
                .collect(Collectors.toList());
        dto.setContents(contentDTOs);
        return dto;
    }

    public static TrainingContentDTO toDto(TrainingContent content) {
        TrainingContentDTO dto = new TrainingContentDTO();
        dto.setId(content.getId());
        dto.setTitle(content.getTitle());
        dto.setContentUrl(content.getContentUrl());
        return dto;
    }

    public static AvisDTO toDto(Avis avis) {
        AvisDTO dto = new AvisDTO();
        dto.setId(avis.getId());
        dto.setAuthorName(avis.getAuthorName());
        dto.setRating(avis.getRating());
        dto.setComment(avis.getComment());
        dto.setCreatedAt(avis.getCreatedAt());
        return dto;
    }

    public static Training toEntity(TrainingRequestDTO dto) {
        Training t = new Training();
        t.setTitle(dto.getTitle());
        t.setDescription(dto.getDescription());
        t.setCategory(dto.getCategory());
        t.setLevel(dto.getLevel());
        t.setPrice(dto.getPrice());
        t.setThumbnailUrl(dto.getThumbnailUrl());
        t.setLanguage(dto.getLanguage());
        t.setStatus(dto.getStatus());
        return t;
    }
}

