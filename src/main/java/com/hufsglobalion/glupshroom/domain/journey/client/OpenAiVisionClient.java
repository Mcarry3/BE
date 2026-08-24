package com.hufsglobalion.glupshroom.domain.journey.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hufsglobalion.glupshroom.global.exception.CustomException;
import com.hufsglobalion.glupshroom.global.exception.ErrorCode;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class OpenAiVisionClient {

    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String SYSTEM_PROMPT = """
            당신은 사용자가 업로드한 사진을 보고, 아끼는 물건과 함께한 여정을 분석하는 도우미입니다.
            사진 속 장면을 보고 활동(activityTag)·상황(situationTag)·스타일(styleTag) 태그를 각각 한 단어~짧은 구로 뽑고,
            그 장면을 바탕으로 감성적인 회고 문장(recallText)을 1~2문장으로 작성하세요.
            activityTag, situationTag, styleTag, recallText 값은 전부 반드시 한국어로 작성하세요. 영어를 섞지 마세요.
            사실을 지어내지 말고, 사진에서 실제로 보이는 것에 근거하세요.
            특정 인물(이름), 사적인 사건, 개인정보는 언급하지 마세요.
            반드시 아래 JSON 형식으로만, 다른 텍스트 없이 응답하세요:
            {"activityTag": "...", "situationTag": "...", "styleTag": "...", "recallText": "..."}
            """;

    private static final List<VisionAnalysisResult> OFFLINE_PRESETS = List.of(
            new VisionAnalysisResult("여행", "낯선 도시의 오후", "캐주얼", "낯선 골목을 걷다가 문득 이 순간을 기록하고 싶어졌어요."),
            new VisionAnalysisResult("출근", "바쁜 아침", "포멀", "분주한 아침, 늘 곁에 있어준 든든한 동반자예요."),
            new VisionAnalysisResult("데이트", "봄나들이", "캐주얼", "봄바람을 맞으며 걷던 그날의 설렘이 아직 남아있어요."),
            new VisionAnalysisResult("모임", "친구들과의 저녁", "스트리트", "오랜만에 만난 얼굴들 사이에서 함께한 시간이었어요."),
            new VisionAnalysisResult("일상", "평범한 하루", "미니멀", "특별할 것 없던 하루도 함께라면 기억할 만한 순간이 돼요."),
            new VisionAnalysisResult("여행", "바다를 마주한 순간", "미니멀", "파도 소리를 들으며 잠시 모든 걸 내려놓았던 기억이에요.")
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final boolean offlineMode;

    public OpenAiVisionClient(
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${app.offline-mode:false}") boolean offlineMode
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.offlineMode = offlineMode;
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(20000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public VisionAnalysisResult analyze(byte[] imageBytes, String contentType, String tone) {
        if (offlineMode) {
            log.info("offline-mode: OpenAI 호출을 건너뛰고 캐시된 여정 분석 결과를 반환합니다");
            return OFFLINE_PRESETS.get(ThreadLocalRandom.current().nextInt(OFFLINE_PRESETS.size()));
        }
        try {
            String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text", "회고 톤: " + tone),
                                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                            ))
                    ),
                    "temperature", 0.7
            );

            Map<String, Object> response = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return parseResult(response);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 여정 분석 실패", e);
            throw new CustomException(ErrorCode.JOURNEY_ANALYSIS_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private VisionAnalysisResult parseResult(Map<String, Object> response) {
        if (response == null) {
            throw new CustomException(ErrorCode.JOURNEY_ANALYSIS_FAILED);
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new CustomException(ErrorCode.JOURNEY_ANALYSIS_FAILED);
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Object content = message == null ? null : message.get("content");
        if (content == null) {
            throw new CustomException(ErrorCode.JOURNEY_ANALYSIS_FAILED);
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(content.toString(), Map.class);
            return new VisionAnalysisResult(
                    stringOrNull(parsed.get("activityTag")),
                    stringOrNull(parsed.get("situationTag")),
                    stringOrNull(parsed.get("styleTag")),
                    stringOrNull(parsed.get("recallText"))
            );
        } catch (Exception e) {
            log.error("AI 응답 파싱 실패: {}", content, e);
            throw new CustomException(ErrorCode.JOURNEY_ANALYSIS_FAILED);
        }
    }

    private String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
