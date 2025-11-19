package com.paybridge.Services;

import com.paybridge.Models.Entities.CustomUserDetails;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Users users = userRepository.findByEmail(email);

        if(users == null){
           throw new UsernameNotFoundException("Email not found");
        }

        return new CustomUserDetails(users);
    }
}
