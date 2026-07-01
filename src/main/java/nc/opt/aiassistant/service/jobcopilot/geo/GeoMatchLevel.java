package nc.opt.aiassistant.service.jobcopilot.geo;

/** How closely two locations match, from an exact city hit down to a confident mismatch. */
public enum GeoMatchLevel {
    EXACT,
    AGGLOMERATION,
    REGION,
    COUNTRY,
    REMOTE,
    UNKNOWN,
    MISMATCH,
}
