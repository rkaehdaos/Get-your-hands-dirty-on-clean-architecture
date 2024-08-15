package dev.haja.buckpal.account.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountRepository
        extends JpaRepository<AccountJPAEntity, Long> {}

@Entity
@Data @EqualsAndHashCode(of = {"id"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AccountJPAEntity {
    @Id @GeneratedValue
    private Long id;
}