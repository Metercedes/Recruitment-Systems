package com.example.recruitmentsystem.security;

import com.example.recruitmentsystem.model.User;
import com.example.recruitmentsystem.service.UserDirectory;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserDirectory directory;

    public AppUserDetailsService(UserDirectory directory) {
        this.directory = directory;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = directory.find(username)
                .orElseThrow(() -> new UsernameNotFoundException("No such account"));

        List<SimpleGrantedAuthority> authorities = user.isAdmin()
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                          new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"),
                          new SimpleGrantedAuthority("ROLE_" + user.getUserType().toUpperCase()));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .accountLocked(directory.isLockedOut(username))
                .build();
    }
}
