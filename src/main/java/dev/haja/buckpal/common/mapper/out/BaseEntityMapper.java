package dev.haja.buckpal.common.mapper.out;

import java.util.List;
import org.mapstruct.Mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

@Mapper(componentModel = SPRING)
public interface BaseEntityMapper<D, E> {

    E toEntity(D domain);

    D toDomain(E entity);

    List<E> toEntity(List<D> domainList);

    List<D> toDomain(List<E> entityList);
}