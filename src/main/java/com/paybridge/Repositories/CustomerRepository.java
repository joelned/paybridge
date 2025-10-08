package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CustomerRepository extends JpaRepository<Customer, Long> {

}
