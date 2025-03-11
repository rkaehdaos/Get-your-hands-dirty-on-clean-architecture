package dev.haja.buckpal.common.mapper.in;

import java.util.List;

public interface BaseCommandMapper<D, C> {

    C toCommand(D domain);

    D toDomain(C command);

    List<C> toCommand(List<D> domainList);

    List<D> toDomain(List<C> commandList);
}