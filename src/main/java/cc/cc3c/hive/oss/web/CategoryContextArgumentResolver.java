package cc.cc3c.hive.oss.web;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.oss.service.FileCategoryResolver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
public class CategoryContextArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String CATEGORY_VARIABLE = "category";

    private final FileCategoryResolver fileCategoryResolver;

    public CategoryContextArgumentResolver(FileCategoryResolver fileCategoryResolver) {
        this.fileCategoryResolver = fileCategoryResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CategoryContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String requestedCode = resolveCategoryCode(webRequest);
        FileCategoryEntity category;
        try {
            category = fileCategoryResolver.resolveCategory(requestedCode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        if (StringUtils.isBlank(category.getBucketName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "bucket name not configured for category: " + requestedCode);
        }
        return new CategoryContext(requestedCode, category.getCode(), category.getId(),
                category.getBucketName(), category.getStorageClass(), category.getUiVariant());
    }

    @SuppressWarnings("unchecked")
    private String resolveCategoryCode(NativeWebRequest webRequest) {
        Map<String, String> variables = (Map<String, String>) webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        if (variables == null || StringUtils.isBlank(variables.get(CATEGORY_VARIABLE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "path variable is required: " + CATEGORY_VARIABLE);
        }
        return variables.get(CATEGORY_VARIABLE);
    }
}
