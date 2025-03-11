package dev.haja.buckpal.common.mapper.out;

import java.util.List;

public interface BaseEntityMapper<D, E> {

    E toEntity(D domain);

    D toDomain(E entity);

    List<E> toEntity(List<D> domainList);

    List<D> toDomain(List<E> entityList);
}
