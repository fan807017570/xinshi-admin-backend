/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.user;

import com.xinshi.admin.application.user.command.CreateUserCommand;
import com.xinshi.admin.domain.shared.DomainException;
import com.xinshi.admin.domain.user.User;
import com.xinshi.admin.domain.user.UserId;
import com.xinshi.admin.domain.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserApplicationService {
    private final UserRepository userRepository;

    public UserApplicationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(CreateUserCommand command) {
        this.userRepository.findByUsername(command.getUsername()).ifPresent(existing -> {
            throw new DomainException("Username already exists");
        });
        User user = User.create(command.getUsername(), command.getDisplayName(), command.getEmail());
        return this.userRepository.save(user);
    }

    public User getUser(String id) {
        return this.userRepository.findById(UserId.of(id)).orElseThrow(() -> new DomainException("User not found"));
    }

    public List<User> listUsers() {
        return this.userRepository.findAll();
    }
}

