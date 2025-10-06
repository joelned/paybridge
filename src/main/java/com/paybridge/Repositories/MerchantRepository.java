package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

}

