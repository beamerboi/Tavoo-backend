package org.tavoo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tavoo.dto.CreateMenuItemRequest;
import org.tavoo.entity.MenuCategory;
import org.tavoo.entity.CourseType;
import org.tavoo.exception.BusinessRuleException;
import org.tavoo.repository.MenuItemRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private MenuService menuService;

    @Test
    void rejectsInvalidPriceEvenWhenCalledOutsideControllerValidation() {
        CreateMenuItemRequest request = new CreateMenuItemRequest(
                "Pasta",
                BigDecimal.ZERO,
                MenuCategory.FOOD,
                CourseType.PRIMO,
                true
        );

        assertThatThrownBy(() -> menuService.createMenuItem(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("price must be positive");

        verifyNoInteractions(menuItemRepository);
    }
}
