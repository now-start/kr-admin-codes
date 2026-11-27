package org.nowstart.kradmincodes.sdk;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 지금 쓰고 있는 스냅샷 정보. {@code dataDate}가 오래됐으면 원천 갱신이 멈췄거나 원격에 닿지 못하는 것이다.
 *
 * @param version 내용 기반 버전 (meta.tsv의 version)
 * @param dataDate 원천 데이터 기준일
 * @param origin {@code "bundled"} 또는 받아온 원격 주소
 * @param loadedAt 이 스냅샷을 올린 시각
 * @param lastCheckedAt 원격에서 마지막으로 응답을 받은 시각, 아직 없으면 null
 */
public record SnapshotInfo(String version, LocalDate dataDate, String origin, Instant loadedAt, Instant lastCheckedAt) {
}
