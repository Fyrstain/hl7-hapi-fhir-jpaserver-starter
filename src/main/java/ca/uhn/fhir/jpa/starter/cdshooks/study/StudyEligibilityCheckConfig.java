package ca.uhn.fhir.jpa.starter.cdshooks.study;

import ca.uhn.fhir.jpa.starter.cdshooks.study.r5.StudyEligibilityCheckService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class StudyEligibilityCheckConfig {
    @Bean(name = "cdsServices")
    public List<Object> cdsServices() {
        List<Object> retVal = new ArrayList<>();
        retVal.add(new StudyEligibilityCheckService());
        return retVal;
    }
}
