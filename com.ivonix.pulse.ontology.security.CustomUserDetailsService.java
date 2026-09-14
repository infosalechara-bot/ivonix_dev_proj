package com.ivonix.pulse.ontology.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public CustomUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Assuming you have a 'users' table with columns: username, password, roles
        String sql = "SELECT username, password, roles FROM public.users WHERE username = ?";
        
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> 
                User.builder()
                    .username(rs.getString("username"))
                    .password(rs.getString("password")) // IMPORTANT: This must be BCrypt encoded!
                    .roles(rs.getString("roles").split(",")) // e.g., "USER,ADMIN"
                    .build()
            , username);
        } catch (Exception e) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
    }
}
