package com.dropie.domain.recommendation.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.event.service.PopularEventService;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.recommendation.client.ClaudeApiClient;
import com.dropie.domain.recommendation.dto.response.RecommendationResponse;
import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.tag.repository.TagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private TasteTagService tasteTagService;
    @Mock private TagRepository tagRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock private PopularEventService popularEventService;
    @Mock private ClaudeApiClient claudeApiClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(any())).willReturn(null);
    }

    @Test
    @DisplayName("시나리오 1: 주문 X + 회원가입 태그 X → 인기 폴백")
    void 시나리오1_둘다_없음() throws Exception {
        given(tasteTagService.getTopTagIds(1L, 5)).willReturn(List.of());
        given(popularEventService.getTopEventIds(any(Integer.class))).willReturn(List.of(99L));

        Product product = Product.builder().id(200L).name("티라미수").price(7000).build();
        Event event = Event.builder().id(99L).brandName("인기브랜드").status(EventStatus.OPEN)
                .startAt(LocalDateTime.now()).endAt(LocalDateTime.now().plusHours(1))
                .products(List.of(product)).build();
        given(eventRepository.findAllById(List.of(99L))).willReturn(List.of(event));
        given(claudeApiClient.generate(any())).willReturn("요즘 인기 있는 디저트예요.");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        List<RecommendationResponse> result = recommendationService.getRecommendation(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("시나리오 2~4: ZSET에 점수 있음 → 태그 매칭 결과 반환")
    void 시나리오234_zset_있음() throws Exception {
        // 시드든 누적이든 합산이든 ZSET에 결과만 있으면 같은 흐름
        given(tasteTagService.getTopTagIds(1L, 5)).willReturn(List.of(10L));
        given(tagRepository.findAllByIdIn(List.of(10L)))
                .willReturn(List.of(Tag.builder().id(10L).name("초콜릿").build()));

        Product product = Product.builder().id(100L).name("초코크루아상").price(5000).description("진한 초콜릿").build();
        Event event = Event.builder().id(1L).brandName("브랜드").status(EventStatus.OPEN)
                .startAt(LocalDateTime.now()).endAt(LocalDateTime.now().plusHours(1))
                .products(List.of(product)).build();
        given(eventRepository.findCandidatesByTagIds(any(), any())).willReturn(List.of(event));
        given(claudeApiClient.generate(any())).willReturn("초콜릿 좋아하시면 마음에 드실 거예요.");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        List<RecommendationResponse> result = recommendationService.getRecommendation(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).contains("초콜릿");
    }

    @Test
    @DisplayName("Claude 실패 → 기본 문구 폴백")
    void Claude_실패_기본문구() throws Exception {
        given(tasteTagService.getTopTagIds(1L, 5)).willReturn(List.of(10L));
        given(tagRepository.findAllByIdIn(any())).willReturn(List.of(Tag.builder().id(10L).name("초콜릿").build()));

        Product product = Product.builder().id(100L).name("크루아상").price(5000).build();
        Event event = Event.builder().id(1L).brandName("노티드").status(EventStatus.OPEN)
                .startAt(LocalDateTime.now()).endAt(LocalDateTime.now().plusHours(1))
                .products(List.of(product)).build();
        given(eventRepository.findCandidatesByTagIds(any(), any())).willReturn(List.of(event));
        given(claudeApiClient.generate(any())).willThrow(new RuntimeException("timeout"));
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        List<RecommendationResponse> result = recommendationService.getRecommendation(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).contains("노티드");
    }

    @Test
    @DisplayName("태그는 있는데 매칭 이벤트 없음 → 인기 폴백")
    void 매칭_없음_인기_폴백() throws Exception {
        given(tasteTagService.getTopTagIds(1L, 5)).willReturn(List.of(10L));
        given(tagRepository.findAllByIdIn(any())).willReturn(List.of(Tag.builder().id(10L).name("초콜릿").build()));
        given(eventRepository.findCandidatesByTagIds(any(), any())).willReturn(List.of());
        given(popularEventService.getTopEventIds(any(Integer.class))).willReturn(List.of(99L));

        Product product = Product.builder().id(200L).name("티라미수").price(7000).build();
        Event event = Event.builder().id(99L).brandName("인기브랜드").status(EventStatus.OPEN)
                .startAt(LocalDateTime.now()).endAt(LocalDateTime.now().plusHours(1))
                .products(List.of(product)).build();
        given(eventRepository.findAllById(List.of(99L))).willReturn(List.of(event));
        given(claudeApiClient.generate(any())).willReturn("요즘 인기 있는 디저트예요.");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        List<RecommendationResponse> result = recommendationService.getRecommendation(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("후보가 0개 → 빈 리스트")
    void 후보_없음_빈리스트() {
        given(tasteTagService.getTopTagIds(1L, 5)).willReturn(List.of());
        given(popularEventService.getTopEventIds(any(Integer.class))).willReturn(List.of());

        List<RecommendationResponse> result = recommendationService.getRecommendation(1L);
        assertThat(result).isEmpty();
    }

}