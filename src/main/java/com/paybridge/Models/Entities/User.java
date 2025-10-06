package com.paybridge.Models.Entities;

import com.paybridge.Models.Enums.UserType;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table
public class User {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )

    private UUID id;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Enumerated(EnumType.STRING)
    private UserType userType;

    private String email;

    private String password;

    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant; // will be null if admin


    private boolean enabled;


    public boolean isMerchant(){
        return userType == UserType.MERCHANT;
    }

    public boolean isAdmin(){
        return userType == UserType.ADMIN;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }


    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
