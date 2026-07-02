package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * Request for the predictive career-path simulator.
 *
 * @param targetRole   the senior role the candidate wants to reach (e.g. "Lead Architect Cloud").
 * @param horizonYears planning horizon in years (typically 2 or 5); drives how deep the roadmap goes.
 */
public record CareerSimulationRequestDto(String targetRole, int horizonYears) {}
