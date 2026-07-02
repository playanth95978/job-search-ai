package nc.opt.aiassistant.web.rest.jobcopilot;

import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.service.jobcopilot.career.CareerPathService;
import nc.opt.aiassistant.service.jobcopilot.dto.CareerRoadmapDto;
import nc.opt.aiassistant.service.jobcopilot.dto.CareerSimulationRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for the predictive career-path simulator.
 */
@RestController
@RequestMapping("/api/job-copilot/career")
@RequiredArgsConstructor
public class CareerPathResource {

    private final CareerPathService careerPathService;

    @PostMapping("/simulate")
    public ResponseEntity<CareerRoadmapDto> simulate(
        @RequestBody CareerSimulationRequestDto request,
        @RequestParam(required = false) String language
    ) {
        int horizon = request.horizonYears() > 0 ? request.horizonYears() : 5;
        return ResponseEntity.ok(careerPathService.simulate(request.targetRole(), horizon, language));
    }
}
