package nc.opt.aiassistant.service.jobcopilot.panel;

/**
 * The three members of the "Shadow Interview Panel". Each has a display label (used to tag the
 * transcript and drive the UI) and a persona prompt describing its focus and tone.
 */
public enum JurorRole {
    HR(
        "RH",
        "Camille Rivière",
        """
        Tu es Camille Rivière, la Responsable RH du jury de recrutement. Bienveillante mais rigoureuse.
        Tu évalues le savoir-être, les soft skills, la motivation, l'adéquation à la culture
        d'entreprise et les attentes salariales. Tes questions sont comportementales et humaines
        (mises en situation, gestion de conflit, motivation, rémunération).
        """
    ),
    TECH_LEAD(
        "Tech Lead",
        "Alex Moreau",
        """
        Tu es Alex Moreau, le Tech Lead du jury de recrutement. Exigeant et précis.
        Tu poses des questions techniques pointues, tu challenges l'architecture, la robustesse,
        les choix technologiques et l'expérience pratique. Tu creuses les réponses vagues.
        """
    ),
    MANAGER(
        "Manager Produit",
        "Julien Bernard",
        """
        Tu es Julien Bernard, le Manager Produit du jury de recrutement. Orienté business et delivery.
        Tu évalues la gestion des priorités, le travail en équipe, le respect des délais,
        la communication et la compréhension des enjeux produit/métier.
        """
    );

    private final String label;
    private final String name;
    private final String persona;

    JurorRole(String label, String name, String persona) {
        this.label = label;
        this.name = name;
        this.persona = persona;
    }

    public String label() {
        return label;
    }

    public String displayName() {
        return name;
    }

    public String persona() {
        return persona;
    }

    /** Lenient parsing of an LLM-provided role name; falls back to {@code HR}. */
    public static JurorRole from(String raw) {
        if (raw != null) {
            String n = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
            for (JurorRole r : values()) {
                if (r.name().equals(n)) {
                    return r;
                }
            }
        }
        return HR;
    }
}
