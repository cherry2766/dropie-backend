package com.dropie.global.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

// 이메일 HTML 템플릿을 렌더링하는 공용 서비스
// → 기존에는 각 서비스 코드에 HTML 문자열을 직접 박아 넣었지만,
//   Thymeleaf 템플릿(resources/templates/email/*.html)으로 분리해
//   디자인 수정이 코드/배포 없이 가능하도록 개선
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final TemplateEngine templateEngine;

    /**
     * 템플릿 경로와 변수(Map)를 받아 렌더링된 HTML 문자열을 반환한다.
     *
     * @param templateName 템플릿 경로 (예: "email/email-verification")
     *                     - resources/templates/ 기준 상대 경로
     *                     - 확장자(.html) 생략
     * @param variables    템플릿에 주입할 변수 (예: Map.of("verifyUrl", "..."))
     * @return 렌더링된 HTML 문자열
     */
    public String render(String templateName, Map<String, Object> variables) {
        // Context: 템플릿 렌더링 시 사용할 변수 보관 객체
        // → 한글 깨짐 방지를 위해 UTF-8 locale/encoding 기반으로 구성
        Context context = new Context();
        context.setVariables(variables);

        // process(): 템플릿 파일을 찾아 변수 주입 후 HTML 문자열로 변환
        return templateEngine.process(templateName, context);
    }

    // 인코딩 상수 (추후 첨부파일 등 확장 시 재사용)
    public static final String CHARSET = StandardCharsets.UTF_8.name();
}
