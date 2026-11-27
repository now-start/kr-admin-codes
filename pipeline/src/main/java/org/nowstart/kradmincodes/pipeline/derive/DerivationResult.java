package org.nowstart.kradmincodes.pipeline.derive;

import java.util.List;
import org.nowstart.kradmincodes.model.Succession;

/** 변환표 도출 결과: 이어진 코드들과, 이어지지 못한 말소 코드들. */
public record DerivationResult(List<Succession> successions, List<Unresolved> unresolved) {

    public DerivationResult {
        successions = List.copyOf(successions);
        unresolved = List.copyOf(unresolved);
    }
}
