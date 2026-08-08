package nc.opt.aiassistant.web.rest.jobcopilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.AutoApplyConfig;
import nc.opt.aiassistant.domain.enumeration.AutoApplyMode;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.AutoApplyAgentService;
import nc.opt.aiassistant.service.jobcopilot.dto.AutoApplyConfigDto;
import nc.opt.aiassistant.service.jobcopilot.dto.AutoApplyRunResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Auto-apply agent API: read/update the per-user configuration and trigger a run on demand.
 */
@RestController
@RequestMapping("/api/job-copilot/auto-apply")
@RequiredArgsConstructor
@Slf4j
public class AutoApplyResource {

    private final AutoApplyAgentService agentService;
    private final ObjectMapper objectMapper;

    @GetMapping("/config")
    public ResponseEntity<AutoApplyConfigDto> getConfig() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(toDto(agentService.getConfig(userId)));
    }

    @PutMapping("/config")
    public ResponseEntity<AutoApplyConfigDto> saveConfig(@RequestBody AutoApplyConfigDto dto) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(toDto(agentService.saveConfig(userId, dto)));
    }

    /** {@code POST /run} : run the agent once for the current user and return a per-offer summary. */
    @PostMapping("/run")
    public ResponseEntity<AutoApplyRunResultDto> run() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        log.info("Auto-apply run requested by user {}", userId);
        return ResponseEntity.ok(agentService.run(userId));
    }

    private AutoApplyConfigDto toDto(AutoApplyConfig config) {
        AutoApplyMode mode = config.getMode() != null ? config.getMode() : AutoApplyMode.VALIDATION;
        double minScore = config.getMinScore() != null ? config.getMinScore() : 0.7;
        int maxPerDay = config.getMaxPerDay() != null ? config.getMaxPerDay() : 5;
        return new AutoApplyConfigDto(mode.name(), minScore, maxPerDay, parseSources(config.getSources()));
    }

    private List<String> parseSources(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
