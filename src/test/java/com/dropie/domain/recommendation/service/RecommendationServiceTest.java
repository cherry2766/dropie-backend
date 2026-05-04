package com.dropie.domain.recommendation.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.event.service.PopularEventService;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.preference.repository.UserPreferenceRepository;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
    @Mock
    private UserPreferenceRepository userPreferenceRepository;
    @Mock
    private OrderRepository orderRepository;

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
        // syncFromDbIfEmpty의 (userId, 1) 호출과 본 흐름의 (userId, 5) 호출 모두 매칭
        given(tasteTagService.getTopTagIds(eq(1L), anyInt())).willReturn(List.of());
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
        // (userId, 1) sync 체크와 (userId, 5) 본 호출 모두 동일 stub 사용 → sync 스킵됨
        given(tasteTagService.getTopTagIds(eq(1L), anyInt())).willReturn(List.of(10L));
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
        given(tasteTagService.getTopTagIds(eq(1L), anyInt())).willReturn(List.of(10L));
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
        given(tasteTagService.getTopTagIds(eq(1L), anyInt())).willReturn(List.of(10L));
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
        given(tasteTagService.getTopTagIds(eq(1L), anyInt())).willReturn(List.of());
        given(popularEventService.getTopEventIds(any(Integer.class))).willReturn(List.of());

        List<RecommendationResponse> result = recommendationService.getRecommendation(1L);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("기존 회원 - ZSET 비어있으면 DB의 회원가입 태그 + 주문 태그로 시드 (lazy 동기화 단위 검증)")
    void 기존회원_ZSET_lazy_동기화() {
        // given: ZSET 첫 호출은 빈 리스트 → 시드 진입
        given(tasteTagService.getTopTagIds(1L, 1)).willReturn(List.of());
        given(userPreferenceRepository.findTagIdsByUserId(1L)).willReturn(List.of(10L, 20L));
        given(orderRepository.findPaidOrderTagIdsByUserId(1L)).willReturn(List.of(10L, 30L));

        // when
        recommendationService.getRecommendation(1L);

        // then: DB → ZSET 시드 호출만 검증 (추천 생성 흐름은 다른 테스트가 커버)
        then(tasteTagService).should().addSeedScores(1L, List.of(10L, 20L));
        then(tasteTagService).should().addTagScores(1L, List.of(10L, 30L));
    }

    @Test
    @DisplayName("ZSET에 이미 점수 있으면 lazy 동기화 스킵 (멱등성)")
    void ZSET_이미_있으면_시드_스킵() {
        // given
        given(tasteTagService.getTopTagIds(1L, 1)).willReturn(List.of(10L));
        given(tasteTagService.getTopTagIds(1L, 5)).willReturn(List.of(10L));
        // ...

        // when
        recommendationService.getRecommendation(1L);

        // then
        then(userPreferenceRepository).should(never()).findTagIdsByUserId(any());
        then(orderRepository).should(never()).findPaidOrderTagIdsByUserId(any());
    }

    @Test
    @DisplayName("DB도 비어있으면 ZSET 그대로 비어있고 인기 폴백으로 진행")
    void DB도_비면_인기_폴백() {
        // given
        given(tasteTagService.getTopTagIds(1L, 1)).willReturn(List.of());
        given(userPreferenceRepository.findTagIdsByUserId(1L)).willReturn(List.of());
        given(orderRepository.findPaidOrderTagIdsByUserId(1L)).willReturn(List.of());
        given(tasteTagService.getTopTagIds(1L, 5)).willReturn(List.of());
        // 인기 폴백 stub
        given(popularEventService.getTopEventIds(anyInt())).willReturn(List.of(1L, 2L));
        // ...

        // when
        List<RecommendationResponse> result = recommendationService.getRecommendation(1L);

        // then - 시드 호출 안 됐는지 확인
        then(tasteTagService).should(never()).addSeedScores(anyLong(), any());
        then(tasteTagService).should(never()).addTagScores(anyLong(), any());
    }

    @Test
    @DisplayName("빈 결과는 캐시하지 않음 - 다음 호출 시 매번 새로 계산")
    void 빈_결과_캐시_스킵() {
        // given: 추천 결과가 빈 배열로 떨어지는 상황 (ZSET 비고, DB도 비고, 인기도 비어있음)
        given(tasteTagService.getTopTagIds(anyLong(), anyInt())).willReturn(List.of());
        given(popularEventService.getTopEventIds(anyInt())).willReturn(List.of());

        // when
        List<RecommendationResponse> result = recommendationService.getRecommendation(1L);

        // then
        assertThat(result).isEmpty();
        // 캐시 set이 호출되지 않았는지 검증
        then(valueOps).should(never()).set(anyString(), anyString(), any(Duration.class));
    }

}