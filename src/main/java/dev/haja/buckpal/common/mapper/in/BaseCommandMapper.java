package dev.haja.buckpal.common.mapper.in;

import java.util.List;
import org.mapstruct.Mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

@Mapper(componentModel = SPRING)
public interface BaseCommandMapper<D, C> {

    C toCommand(D domain);

    D toDomain(C command);

    List<C> toCommand(List<D> domainList);

    List<D> toDomain(List<C> commandList);
}