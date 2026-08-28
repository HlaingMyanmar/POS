package org.sspd.servicemgmt.rbacoptions.useroptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from User u join u.roles r where r.name = :roleName")
    boolean existsByRoleName(@Param("roleName") String roleName);

    Optional<User> findByUsernameOrEmail(String username, String email);

    @Query("select u from User u left join fetch u.staff where u.username = :name or u.email = :name")
    Optional<User> findWithStaffByUsernameOrEmail(@Param("name") String name);
}
