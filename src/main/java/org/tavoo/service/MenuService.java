package org.tavoo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tavoo.dto.CreateMenuItemRequest;
import org.tavoo.dto.MenuItemResponse;
import org.tavoo.entity.CourseType;
import org.tavoo.entity.MenuItem;
import org.tavoo.entity.MenuCategory;
import org.tavoo.exception.BusinessRuleException;
import org.tavoo.repository.MenuItemRepository;

import java.math.RoundingMode;
import java.util.List;

@Service
public class MenuService {

    private final MenuItemRepository menuItemRepository;

    public MenuService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAvailableMenuItems() {
        return menuItemRepository.findByAvailableTrueOrderByNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MenuItemResponse createMenuItem(CreateMenuItemRequest request) {
        String normalizedName = request.name().trim();
        if (request.price().signum() <= 0 || request.price().scale() > 2) {
            throw new BusinessRuleException("Menu item price must be positive and have at most two decimal places");
        }
        if (menuItemRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BusinessRuleException("A menu item named '" + normalizedName + "' already exists");
        }
        validateCourse(request.category(), request.courseType());

        MenuItem menuItem = new MenuItem(
                normalizedName,
                request.price().setScale(2, RoundingMode.UNNECESSARY),
                request.category(),
                request.courseType(),
                request.available()
        );
        return toResponse(menuItemRepository.save(menuItem));
    }

    private MenuItemResponse toResponse(MenuItem item) {
        return new MenuItemResponse(
                item.getId(),
                item.getName(),
                item.getPrice(),
                item.getCategory(),
                item.getCourseType(),
                item.isAvailable()
        );
    }

    private void validateCourse(MenuCategory category, CourseType courseType) {
        boolean valid = switch (category) {
            case FOOD -> courseType == CourseType.ANTIPASTO
                    || courseType == CourseType.PRIMO
                    || courseType == CourseType.SECONDO
                    || courseType == CourseType.STEAK;
            case DESSERT -> courseType == CourseType.DESSERT;
            case BEVERAGE, ALCOHOL -> courseType == CourseType.BEVERAGE;
        };
        if (!valid) {
            throw new BusinessRuleException(
                    "Course type " + courseType + " is not valid for category " + category
            );
        }
    }
}
