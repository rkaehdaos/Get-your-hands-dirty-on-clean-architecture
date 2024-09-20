package dev.haja.buckpal.account.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

interface ActivityRepository extends JpaRepository<ActivityJpaEntity, Long> {

    @Query("SELECT a FROM ActivityJpaEntity a " +
            "WHERE a.ownerAccountId = :ownerAccountId " +
            "AND a.timestamp >= :since")
    List<ActivityJpaEntity> findByOwnerSince(
            @Param("ownerAccountId") Long ownerAccountId,
            @Param("since") LocalDateTime since);

    @Query("SELECT SUM(a.amount) FROM ActivityJpaEntity a " +
            "WHERE a.targetAccountId = :accountId " +
            "AND a.ownerAccountId = :accountId " +
            "AND a.timestamp < :until")
    Long getDepositBalanceUntil(
            @Param("accountId") Long accountId,
            @Param("until") LocalDateTime until);

    @Query("select sum(a.amount) from ActivityJpaEntity a " +
            "where a.sourceAccountId = :accountId " +
            "and a.ownerAccountId = :accountId " +
            "and a.timestamp < :until")
    Long getWithdrawalBalanceUntil(
            @Param("accountId") Long accountId,
            @Param("until") LocalDateTime until);

}