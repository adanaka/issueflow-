package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.comment.CommentService;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    // Spec uses POST /users/update/:userId instead of PATCH /users/:userId
    @PostMapping("/update/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{userId}/mentions")
    public ResponseEntity<Page<CommentResponse>> getMentions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(commentService.getMentionsForUser(userId, page, pageSize));
    }
}
