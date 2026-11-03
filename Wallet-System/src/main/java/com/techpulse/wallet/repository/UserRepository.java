package com.techpulse.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.techpulse.wallet.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	//to check does email does exist in database
	Optional<User> findByEmail(String email);
// to store unique email
    boolean existsByEmail(String email);
	

}
