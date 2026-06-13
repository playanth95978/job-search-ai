package nc.opt.aiassistant.service.jobcopilot.dto;

import java.time.Instant;
import java.util.UUID;

/** Lightweight version-history entry (no {@code data}) for listing past resume snapshots. */
public record ResumeVersionDto(UUID id, Integer version, String title, String template, Instant createdAt) {}
