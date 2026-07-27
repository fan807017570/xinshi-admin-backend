/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.validation.Valid
 *  org.springframework.http.HttpStatus
 *  org.springframework.validation.annotation.Validated
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.ResponseStatus
 *  org.springframework.web.bind.annotation.RestController
 */
package com.xinshi.admin.interfaces.web;

import com.xinshi.admin.application.user.UserApplicationService;
import com.xinshi.admin.application.user.command.CreateUserCommand;
import com.xinshi.admin.interfaces.dto.UserCreateRequest;
import com.xinshi.admin.interfaces.dto.UserResponse;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(value={"/api/demo/users"})
public class UserController {
    private final UserApplicationService userApplicationService;

    public UserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @PostMapping
    @ResponseStatus(value=HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        CreateUserCommand command = new CreateUserCommand(request.getUsername(), request.getDisplayName(), request.getEmail());
        return UserResponse.from(this.userApplicationService.createUser(command));
    }

    @GetMapping(value={"/{id}"})
    public UserResponse getUser(@PathVariable String id) {
        return UserResponse.from(this.userApplicationService.getUser(id));
    }

    @GetMapping
    public List<UserResponse> listUsers() {
        return this.userApplicationService.listUsers().stream().map(UserResponse::from).collect(Collectors.toList());
    }
}

