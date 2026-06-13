package nc.opt.aiassistant.web.rest.jobcopilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.UserPreference;
import nc.opt.aiassistant.repository.jobcopilot.UserPreferenceRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.dto.UserPreferenceDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/job-copilot/preferences")
@RequiredArgsConstructor
public class JobCopilotPreferenceResource {

    private final UserPreferenceRepository preferenceRepository;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<UserPreference> getPreferences() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return preferenceRepository.findByUserId(userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<UserPreference> updatePreferences(@RequestBody UserPreferenceDto dto) throws JsonProcessingException {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        UserPreference pref = preferenceRepository.findByUserId(userId).orElse(new UserPreference());
        pref.setUserId(userId);
        pref.setRemoteOnly(dto.remoteOnly());
        pref.setContractType(dto.contractType());
        pref.setSalaryMin(dto.salaryMin());
        pref.setSalaryMax(dto.salaryMax());
        pref.setPreferredRoles(objectMapper.writeValueAsString(dto.preferredRoles()));
        pref.setExcludedTechnologies(objectMapper.writeValueAsString(dto.excludedTechnologies()));
        pref.setPreferredLocations(objectMapper.writeValueAsString(dto.preferredLocations()));
        return ResponseEntity.ok(preferenceRepository.save(pref));
    }
}
