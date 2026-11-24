package com.lostfound.backend.item;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController {

    public record CategoryResponse(Long id, String name) {
    }

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** Public: the fixed list of categories, used by the frontend's dropdowns. */
    @GetMapping("/api/categories")
    public List<CategoryResponse> list() {
        return categoryRepository.findAll(Sort.by("id")).stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName()))
                .toList();
    }
}
