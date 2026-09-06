package org.tavoo.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tavoo.dto.CreateMenuItemRequest;
import org.tavoo.dto.MenuItemResponse;
import org.tavoo.service.MenuService;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAITER')")
    public List<MenuItemResponse> getAvailableMenuItems() {
        return menuService.getAvailableMenuItems();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MenuItemResponse> createMenuItem(
            @Valid @RequestBody CreateMenuItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createMenuItem(request));
    }
}
