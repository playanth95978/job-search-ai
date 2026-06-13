package nc.opt.aiassistant.service.jobcopilot.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Resume payload exchanged with the CV Builder. {@code data} is the full resume JSON (opaque to
 * the backend), {@code title}/{@code template} are denormalized for listing without parsing.
 */
public record ResumeDto(UUID id, String title, String template, String data, Integer version, Instant updatedAt) {}
