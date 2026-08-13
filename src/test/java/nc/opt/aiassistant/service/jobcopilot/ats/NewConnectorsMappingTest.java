package nc.opt.aiassistant.service.jobcopilot.ats;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.AtsConnector.PostingRef;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que chaque nouveau connecteur mappe bien les champs tels que documentés par son API, et
 * qu'il reconnaît/décompose correctement les URLs de son ATS. Les charges utiles sont copiées de la
 * documentation (ou d'une réponse réelle) de chaque fournisseur : c'est le contrat qu'on teste ici,
 * pas le transport HTTP.
 */
class NewConnectorsMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    class Jobicy {

        private final JobicyConnector connector = new JobicyConnector(mapper);

        private JsonNode sampleJob() {
            return json(
                """
                {
                  "id": 150593,
                  "url": "https://jobicy.com/jobs/150593-ai-solutions-builder",
                  "jobSlug": "150593-ai-solutions-builder",
                  "jobTitle": "AI Solutions Builder",
                  "companyName": "Smartcat",
                  "jobIndustry": ["Software Engineering"],
                  "jobType": ["Full-Time"],
                  "jobGeo": "Europe",
                  "jobLevel": "Senior",
                  "jobExcerpt": "Build AI solutions",
                  "jobDescription": "<p>Java, Spring Boot and Angular</p>",
                  "pubDate": "2026-08-12T14:19:54+00:00",
                  "salaryMin": 90000,
                  "salaryMax": 120000,
                  "salaryCurrency": "EUR",
                  "salaryPeriod": "yearly"
                }
                """
            );
        }

        @Test
        void mapsDocumentedJobFields() {
            JobOffer offer = connector.mapJobOffer(sampleJob());

            assertThat(offer.getTitle()).isEqualTo("AI Solutions Builder");
            assertThat(offer.getCompany()).isEqualTo("Smartcat");
            assertThat(offer.getLocation()).isEqualTo("Europe");
            assertThat(offer.getDescription()).isEqualTo("Java, Spring Boot and Angular");
            assertThat(offer.getApplyUrl()).isEqualTo("https://jobicy.com/jobs/150593-ai-solutions-builder");
            assertThat(offer.getSourceId()).isEqualTo("150593");
            assertThat(offer.getSource()).isEqualTo(JobSource.JOBICY);
            assertThat(offer.getContractType()).isEqualTo("Full-Time");
            assertThat(offer.getExperienceLevel()).isEqualTo("Senior");
            assertThat(offer.getRemote()).isTrue();
            assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-08-12T14:19:54Z"));
            assertThat(offer.getSalaryMin()).isEqualTo(90000);
            assertThat(offer.getSalaryMax()).isEqualTo(120000);
            assertThat(offer.getSalaryCurrency()).isEqualTo("EUR");
        }

        @Test
        void ignoresNonYearlySalary() {
            JobOffer offer = connector.mapJobOffer(
                json("{\"jobTitle\":\"Dev\",\"salaryMin\":50,\"salaryMax\":80,\"salaryCurrency\":\"USD\",\"salaryPeriod\":\"hourly\"}")
            );

            assertThat(offer.getSalaryMin()).isNull();
            assertThat(offer.getSalaryMax()).isNull();
            assertThat(offer.getSalaryCurrency()).isNull();
        }

        @Test
        void fallsBackOnTheExcerptWhenTheDescriptionIsEmpty() {
            JobOffer offer = connector.mapJobOffer(json("{\"jobTitle\":\"Dev\",\"jobExcerpt\":\"Short pitch\"}"));

            assertThat(offer.getDescription()).isEqualTo("Short pitch");
        }

        @Test
        void recognisesItsOwnUrls() {
            assertThat(connector.supports("https://jobicy.com/jobs/150593-ai-solutions-builder")).isTrue();
            assertThat(connector.supports("https://example.com/jobs/1")).isFalse();
            assertThat(connector.parseRef("https://jobicy.com/jobs/150593-ai-solutions-builder").postingId()).isEqualTo("150593");
        }
    }

    @Nested
    class Careerjet {

        private final CareerjetConnector connector = new CareerjetConnector(mapper);

        @Test
        void mapsDocumentedJobFieldsAndYearlySalary() {
            JobOffer offer = connector.mapJobOffer(
                json(
                    """
                    {
                      "title": "Senior Java Developer",
                      "company": "ACME Pty",
                      "date": "Tue, 21 Jul 2026",
                      "description": "Java, Spring Boot, <b>Angular</b>",
                      "locations": "Brisbane, QLD",
                      "salary": "110,000 - 130,000 AUD per year",
                      "salary_min": 110000,
                      "salary_max": 130000,
                      "salary_type": "Y",
                      "salary_currency_code": "AUD",
                      "url": "https://www.careerjet.com.au/jobad/au123"
                    }
                    """
                ),
                "en_AU"
            );

            assertThat(offer.getTitle()).isEqualTo("Senior Java Developer");
            assertThat(offer.getCompany()).isEqualTo("ACME Pty");
            assertThat(offer.getLocation()).isEqualTo("Brisbane, QLD");
            assertThat(offer.getDescription()).isEqualTo("Java, Spring Boot, Angular");
            assertThat(offer.getApplyUrl()).isEqualTo("https://www.careerjet.com.au/jobad/au123");
            assertThat(offer.getSourceId()).isEqualTo("https://www.careerjet.com.au/jobad/au123");
            assertThat(offer.getSource()).isEqualTo(JobSource.CAREERJET);
            assertThat(offer.getCountry()).isEqualTo("AU");
            assertThat(offer.getSalaryMin()).isEqualTo(110000);
            assertThat(offer.getSalaryMax()).isEqualTo(130000);
            assertThat(offer.getSalaryCurrency()).isEqualTo("AUD");
            assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-07-21T00:00:00Z"));
        }

        @Test
        void ignoresNonYearlySalaryToKeepTheColumnHomogeneous() {
            JobOffer offer = connector.mapJobOffer(
                json("{\"title\":\"Dev\",\"salary_min\":45,\"salary_max\":60,\"salary_type\":\"H\",\"salary_currency_code\":\"EUR\"}"),
                "fr_FR"
            );

            assertThat(offer.getSalaryMin()).isNull();
            assertThat(offer.getSalaryMax()).isNull();
            assertThat(offer.getSalaryCurrency()).isNull();
        }
    }

    @Nested
    class Workable {

        private final WorkableConnector connector = new WorkableConnector(mapper);

        @Test
        void mapsFieldsOfTheLiveWidgetPayload() {
            JobOffer offer = connector.mapJobOffer(
                json(
                    """
                    {
                      "title": "Low-level Senior Software Engineer",
                      "shortcode": "F4C096B22E",
                      "employment_type": "Full-time",
                      "telecommuting": true,
                      "department": "Product",
                      "url": "https://apply.workable.com/j/F4C096B22E",
                      "shortlink": "https://apply.workable.com/j/F4C096B22E",
                      "application_url": "https://apply.workable.com/j/F4C096B22E/apply",
                      "published_on": "2026-07-30",
                      "created_at": "2026-07-30",
                      "country": "France",
                      "city": "Paris",
                      "state": "Île-de-France",
                      "experience": "Mid-Senior level",
                      "locations": [{ "country": "France", "countryCode": "FR", "city": "Paris" }],
                      "description": "<p>Role managing storage</p>"
                    }
                    """
                ),
                "Hugging Face"
            );

            assertThat(offer.getTitle()).isEqualTo("Low-level Senior Software Engineer");
            assertThat(offer.getCompany()).isEqualTo("Hugging Face");
            assertThat(offer.getLocation()).isEqualTo("Paris, France");
            assertThat(offer.getCountry()).isEqualTo("FR");
            assertThat(offer.getDescription()).isEqualTo("Role managing storage");
            assertThat(offer.getApplyUrl()).isEqualTo("https://apply.workable.com/j/F4C096B22E/apply");
            assertThat(offer.getSourceId()).isEqualTo("F4C096B22E");
            assertThat(offer.getSource()).isEqualTo(JobSource.WORKABLE);
            assertThat(offer.getContractType()).isEqualTo("Full-time");
            assertThat(offer.getExperienceLevel()).isEqualTo("Mid-Senior level");
            assertThat(offer.getRemote()).isTrue();
            assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-07-30T00:00:00Z"));
        }

        @Test
        void parsesBothPostingUrlShapes() {
            assertThat(connector.parseRef("https://apply.workable.com/j/F4C096B22E").postingId()).isEqualTo("F4C096B22E");

            PostingRef withAccount = connector.parseRef("https://apply.workable.com/acme/j/ABC123");
            assertThat(withAccount.site()).isEqualTo("acme");
            assertThat(withAccount.postingId()).isEqualTo("ABC123");

            assertThat(connector.supports("https://acme.workable.com/jobs")).isTrue();
            assertThat(connector.parseRef("https://example.com/jobs/1")).isNull();
        }
    }

    @Nested
    class Ashby {

        private final AshbyConnector connector = new AshbyConnector(mapper);

        @Test
        void mapsDocumentedPostingFields() {
            JobOffer offer = connector.mapJobOffer(
                json(
                    """
                    {
                      "id": "0b1e4f66-6d1f-4a2c-9a1e-1f2d3c4b5a69",
                      "title": "Platform Engineer",
                      "location": "Remote - Europe",
                      "isListed": true,
                      "isRemote": true,
                      "workplaceType": "Remote",
                      "employmentType": "FullTime",
                      "descriptionPlain": "Build the platform",
                      "descriptionHtml": "<p>Build the platform</p>",
                      "publishedAt": "2026-07-15T08:30:00.000Z",
                      "address": { "postalAddress": { "addressCountry": "FR" } },
                      "jobUrl": "https://jobs.ashbyhq.com/acme/0b1e4f66-6d1f-4a2c-9a1e-1f2d3c4b5a69",
                      "applyUrl": "https://jobs.ashbyhq.com/acme/0b1e4f66-6d1f-4a2c-9a1e-1f2d3c4b5a69/application"
                    }
                    """
                ),
                "acme"
            );

            assertThat(offer.getTitle()).isEqualTo("Platform Engineer");
            assertThat(offer.getCompany()).isEqualTo("acme");
            assertThat(offer.getLocation()).isEqualTo("Remote - Europe");
            assertThat(offer.getCountry()).isEqualTo("FR");
            assertThat(offer.getDescription()).isEqualTo("Build the platform");
            assertThat(offer.getApplyUrl()).endsWith("/application");
            assertThat(offer.getSourceId()).isEqualTo("0b1e4f66-6d1f-4a2c-9a1e-1f2d3c4b5a69");
            assertThat(offer.getSource()).isEqualTo(JobSource.ASHBY);
            assertThat(offer.getContractType()).isEqualTo("FullTime");
            assertThat(offer.getRemote()).isTrue();
            assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-07-15T08:30:00Z"));
        }

        @Test
        void treatsHybridAsNotRemote() {
            JobOffer offer = connector.mapJobOffer(json("{\"title\":\"Dev\",\"isRemote\":false,\"workplaceType\":\"Hybrid\"}"), "acme");

            assertThat(offer.getRemote()).isFalse();
        }

        @Test
        void parsesBoardAndPostingFromUrl() {
            PostingRef ref = connector.parseRef("https://jobs.ashbyhq.com/acme/0b1e4f66-6d1f-4a2c-9a1e-1f2d3c4b5a69");

            assertThat(ref.site()).isEqualTo("acme");
            assertThat(ref.postingId()).isEqualTo("0b1e4f66-6d1f-4a2c-9a1e-1f2d3c4b5a69");
            assertThat(connector.supports("https://jobs.ashbyhq.com/acme")).isTrue();
        }
    }

    @Nested
    class Recruitee {

        private final RecruiteeConnector connector = new RecruiteeConnector(mapper);

        @Test
        void mapsDocumentedOfferFieldsAndMergesRequirements() {
            JobOffer offer = connector.mapJobOffer(
                json(
                    """
                    {
                      "id": 987654,
                      "slug": "developpeur-java",
                      "title": "Développeur Java",
                      "status": "published",
                      "description": "<p>Rejoignez l'équipe</p>",
                      "requirements": "<ul><li>Java 21</li></ul>",
                      "remote": false,
                      "employment_type_code": "fulltime_permanent",
                      "experience_code": "senior",
                      "locations": [{ "city": "Nouméa", "country": "New Caledonia", "country_code": "NC" }],
                      "salary": { "min": 4500, "max": 5500, "currency": "XPF", "period": "month" },
                      "careers_url": "https://acme.recruitee.com/o/developpeur-java",
                      "careers_apply_url": "https://acme.recruitee.com/o/developpeur-java/c/new",
                      "published_at": "2026-07-30T09:12:00.000Z"
                    }
                    """
                ),
                "acme"
            );

            assertThat(offer.getTitle()).isEqualTo("Développeur Java");
            assertThat(offer.getCompany()).isEqualTo("acme");
            assertThat(offer.getLocation()).isEqualTo("Nouméa, New Caledonia");
            assertThat(offer.getCountry()).isEqualTo("NC");
            assertThat(offer.getDescription()).isEqualTo("Rejoignez l'équipe\n\nJava 21");
            assertThat(offer.getApplyUrl()).isEqualTo("https://acme.recruitee.com/o/developpeur-java/c/new");
            assertThat(offer.getSourceId()).isEqualTo("987654");
            assertThat(offer.getSource()).isEqualTo(JobSource.RECRUITEE);
            assertThat(offer.getContractType()).isEqualTo("fulltime_permanent");
            assertThat(offer.getExperienceLevel()).isEqualTo("senior");
            assertThat(offer.getRemote()).isFalse();
            assertThat(offer.getSalaryMin()).isEqualTo(4500);
            assertThat(offer.getSalaryMax()).isEqualTo(5500);
            assertThat(offer.getSalaryCurrency()).isEqualTo("XPF");
            assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-07-30T09:12:00Z"));
        }

        @Test
        void parsesCompanyFromSubdomainAndSlugFromPath() {
            PostingRef ref = connector.parseRef("https://acme.recruitee.com/o/developpeur-java");

            assertThat(ref.site()).isEqualTo("acme");
            assertThat(ref.postingId()).isEqualTo("developpeur-java");
            assertThat(connector.supports("https://acme.recruitee.com/o/dev")).isTrue();
            assertThat(connector.supports("https://example.com/o/dev")).isFalse();
        }
    }
}
