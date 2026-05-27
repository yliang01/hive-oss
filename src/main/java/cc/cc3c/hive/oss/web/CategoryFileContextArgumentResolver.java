package cc.cc3c.hive.oss.web;

import cc.cc3c.hive.domain.model.HiveRecordFileView;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
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
public class CategoryFileContextArgumentResolver implements HandlerMethodArgumentResolver {
    private static final String CATEGORY_VARIABLE = "category";
    private static final String FILE_KEY_VARIABLE = "fileKey";

    private final CategoryContextFactory categoryContextFactory;
    private final HiveRecordRepository hiveRecordRepository;

    public CategoryFileContextArgumentResolver(CategoryContextFactory categoryContextFactory,
                                               HiveRecordRepository hiveRecordRepository) {
        this.categoryContextFactory = categoryContextFactory;
        this.hiveRecordRepository = hiveRecordRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CategoryFileContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Map<String, String> variables = pathVariables(webRequest);
        CategoryContext category = categoryContextFactory.resolve(requiredVariable(variables, CATEGORY_VARIABLE));
        String key = FileKeyNormalizer.normalize(requiredVariable(variables, FILE_KEY_VARIABLE));
        if (StringUtils.isBlank(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "path variable is required: " + FILE_KEY_VARIABLE);
        }
        HiveRecordFileView record = hiveRecordRepository
                .findViewByCategory_IdAndFileKeyAndDeletedIsFalse(category.categoryId(), key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return CategoryFileContext.from(category, key, record);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> pathVariables(NativeWebRequest webRequest) {
        Map<String, String> variables = (Map<String, String>) webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        if (variables == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path variables are required");
        }
        return variables;
    }

    private String requiredVariable(Map<String, String> variables, String name) {
        String value = variables.get(name);
        if (StringUtils.isBlank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path variable is required: " + name);
        }
        return value;
    }
}
