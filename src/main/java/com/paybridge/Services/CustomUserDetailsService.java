package com.paybridge.Services;

import com.paybridge.Models.Entities.CustomUserDetails;
import com.paybridge.Models.Entities.User;
import com.paybridge.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email);

        if(user == null){
           throw new UsernameNotFoundException("Email not found");
        }

        return new CustomUserDetails(user);
    }
}
