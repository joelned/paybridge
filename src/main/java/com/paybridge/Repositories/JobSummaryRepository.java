package com.paybridge.Repositories;

import com.paybridge.Models.Entities.JobSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobSummaryRepository extends JpaRepository<JobSummary, UUID> {

}
