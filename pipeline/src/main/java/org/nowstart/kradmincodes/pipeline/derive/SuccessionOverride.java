package org.nowstart.kradmincodes.pipeline.derive;

import java.time.LocalDate;
import java.util.Objects;
import org.nowstart.kradmincodes.model.Codes;

/**
 * 사람이 변경내역 문서를 보고 입력한 변환. {@code toCode}가 빈 문자열이면 "후계 코드 없음을 확인함"이라는 뜻이다.
 *
 * @param note 근거 (게시글 제목, 문서 이름 등)
 */
public record SuccessionOverride(String fromCode, String toCode, LocalDate effectiveDate, String note) {

    public SuccessionOverride {
        Codes.requireCode(fromCode);
        Objects.requireNonNull(toCode, "toCode");
        if (!toCode.isEmpty()) {
            Codes.requireCode(toCode);
        }
        if (fromCode.equals(toCode)) {
            throw new IllegalArgumentException("옛 코드와 새 코드가 같습니다: " + fromCode);
        }
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(note, "note");
    }

    public boolean hasSuccessor() {
        return !toCode.isEmpty();
    }
}
