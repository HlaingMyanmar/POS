package org.sspd.servicemgmt.rbacoptions.useroptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByUsernameAndIdNot(String username, Long id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /**
     * Prefer username match, then email. Avoids NonUniqueResultException when a
     * username collides with another user's email.
     */
    default Optional<User> findByUsernameOrEmail(String username, String email) {
        if (username != null && !username.isBlank()) {
            Optional<User> byUsername = findByUsername(username);
            if (byUsername.isPresent()) {
                return byUsername;
            }
        }
        if (email != null && !email.isBlank()) {
            return findByEmail(email);
        }
        return Optional.empty();
    }

    @Query("select count(u) > 0 from User u join u.roles r where r.name = :roleName")
    boolean existsByRoleName(@Param("roleName") String roleName);

    @Query("select u from User u left join fetch u.staff where u.username = :name")
    Optional<User> findWithStaffByUsername(@Param("name") String name);

    @Query("select u from User u left join fetch u.staff where u.email = :name")
    Optional<User> findWithStaffByEmail(@Param("name") String name);

    default Optional<User> findWithStaffByUsernameOrEmail(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return findWithStaffByUsername(name).or(() -> findWithStaffByEmail(name));
    }
}
