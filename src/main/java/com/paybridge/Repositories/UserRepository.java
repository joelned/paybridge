package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Users, Long> {
    Users findByEmail(String email);
    Users findByMerchant(Merchant merchant);
}
