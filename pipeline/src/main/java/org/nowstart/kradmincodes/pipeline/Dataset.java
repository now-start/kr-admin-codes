package org.nowstart.kradmincodes.pipeline;

import java.util.List;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.AdminLegalLink;
import org.nowstart.kradmincodes.model.LegalDong;
import org.nowstart.kradmincodes.model.Succession;
import org.nowstart.kradmincodes.pipeline.derive.Unresolved;

/** 파이프라인이 만들어 {@code data/v1}에 쓰는 정규화 결과 전체. */
public record Dataset(
        List<AdminDong> adminDongs,
        List<LegalDong> legalDongs,
        List<AdminLegalLink> links,
        List<Succession> successions,
        List<Unresolved> unresolved) {

    public Dataset {
        adminDongs = List.copyOf(adminDongs);
        legalDongs = List.copyOf(legalDongs);
        links = List.copyOf(links);
        successions = List.copyOf(successions);
        unresolved = List.copyOf(unresolved);
    }
}
