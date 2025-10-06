package com.paybridge.Models.Entities;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table
public class Role {

   @Id
   @GeneratedValue(
           strategy = GenerationType.IDENTITY
   )
   private UUID id;
   private String name;

}
