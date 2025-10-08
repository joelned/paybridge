package com.paybridge.Repositories;

import com.paybridge.Models.Entities.JobSummary;
import org.springframework.data.jpa.repository.JpaRepository;


public interface JobSummaryRepository extends JpaRepository<JobSummary, Long> {

}
