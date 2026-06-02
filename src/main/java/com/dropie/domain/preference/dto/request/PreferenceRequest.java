package com.dropie.domain.preference.dto.request;

// 클라이언트가 POST /users/me/preferences 로 보내는 JSON
// {
//   "tagIds": [1, 2, 3]
// }
// 온보딩에서 사용자가 선택한 태그 ID 목록을 받음
// score는 클라이언트에서 받지 않고 서버에서 기본값으로 세팅 (온보딩 = 동일 가중치)

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PreferenceRequest {

    private List<Long> tagIds;
}
