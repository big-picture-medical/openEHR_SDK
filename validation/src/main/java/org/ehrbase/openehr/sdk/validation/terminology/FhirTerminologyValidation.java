/*
 * Copyright (c) 2021 vitasystems GmbH and Hannover Medical School.
 *
 * This file is part of project openEHR_SDK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehrbase.openehr.sdk.validation.terminology;

import static java.lang.String.format;

import com.google.common.net.HttpHeaders;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.nedap.archie.rm.datatypes.CodePhrase;
import com.nedap.archie.rm.datavalues.DvCodedText;
import com.nedap.archie.rm.support.identification.TerminologyId;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.ehrbase.openehr.sdk.util.functional.Try;
import org.ehrbase.openehr.sdk.validation.ConstraintViolation;
import org.ehrbase.openehr.sdk.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ExternalTerminologyValidation} that supports FHIR terminology validation.
 */
public class FhirTerminologyValidation implements ExternalTerminologyValidation {
    private static final Logger LOG = LoggerFactory.getLogger(FhirTerminologyValidation.class);
    private final String baseUrl;
    private final boolean failOnError;
    private final Executor executor;

    public FhirTerminologyValidation(String baseUrl) {
        this(baseUrl, true);
    }

    public FhirTerminologyValidation(String baseUrl, boolean failOnError) {
        this(baseUrl, failOnError, HttpClients.createDefault());
    }

    public FhirTerminologyValidation(String baseUrl, boolean failOnError, HttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.failOnError = failOnError;
        executor = Executor.newInstance(httpClient);
    }

    private String extractUrl(String referenceSetUri) {
        return StringUtils.substringAfter(referenceSetUri, "url=");
    }

    private DocumentContext internalGet(String uri) throws IOException {
        Request request = Request.Get(uri).addHeader(HttpHeaders.ACCEPT, "application/fhir+json");

        HttpResponse response = executor.execute(request).returnResponse();
        String responseBody = Optional.ofNullable(response.getEntity())
                .map(entity -> {
                    try {
                        return EntityUtils.toString(response.getEntity());
                    } catch (IOException e) {
                        return null;
                    }
                })
                .orElse("");

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != HttpStatus.SC_OK) {
            throw new ExternalTerminologyValidationException(
                    "Error response received from the terminology server. HTTP status: " + statusCode + ". Body: "
                            + responseBody);
        }

        return JsonPath.parse(responseBody);
    }

    static final String SUPPORTS_CODE_SYS_TEMPL = "%s/CodeSystem?url=%s";
    static final String SUPPORTS_VALUE_SET_TEMPL = "%s/ValueSet?url=%s";
    private static final String ERR_SUPPORTS =
            "An error occurred while checking if FHIR terminology server supports the referenceSetUri: %s";
    static final String CODE_PHRASE_TEMPL = "code=%s&system=%s";
    private static final String ERR_EXPAND_VALUESET = "Error while expanding ValueSet[%s]";
    static final String EXPAND_VALUE_SET_TEMPL = "%s/ValueSet/$expand?%s";

    static String renderTempl(String templ, String... args) {
        return format(templ, args);
    }

    @SuppressWarnings({"serial"})
    private final Set<String> acceptedFhirApis = new HashSet<>() {
        {
            add("//fhir.hl7.org");
            add("terminology://fhir.hl7.org");
            add("//hl7.org/fhir");
        }

        public String toString() {
            return this.stream().collect(Collectors.joining(", "));
        }
    };

    private boolean isValidTerminology(Optional<String> url) {
        if (url.isEmpty()) return false;
        return acceptedFhirApis.stream()
                .filter(api -> url.get().startsWith(api))
                .map(api -> Boolean.TRUE)
                .findFirst()
                .orElse(Boolean.FALSE);
    }

    @Override
    public boolean supports(TerminologyParam param) {
        String url = null;
        Optional<String> urlParam = param.extractFromParameter(p -> Optional.ofNullable(extractUrl(p)));
        if (urlParam.isEmpty() || !isValidTerminology(param.getServiceApi())) return false;

        if (param.isUseValueSet()) url = renderTempl(SUPPORTS_VALUE_SET_TEMPL, baseUrl, urlParam.get());
        else if (param.isUseCodeSystem()) url = renderTempl(SUPPORTS_CODE_SYS_TEMPL, baseUrl, urlParam.get());
        else return false;

        try {
            return internalGet(url).read("$.total", int.class) > 0;
        } catch (IOException e) {
            if (failOnError) throw new ExternalTerminologyValidationException(format(ERR_SUPPORTS, url), e);
            LOG.warn("The following error occurred: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Try<Boolean, ConstraintViolationException> validate(TerminologyParam param) {
        Optional<String> url = param.extractFromParameter(p -> Optional.ofNullable(extractUrl(p)));

        if (url.isEmpty()) {
            return Try.failure(
                    new ConstraintViolationException(List.of(new ConstraintViolation("Missing value-set url"))));
        }

        if (param.isUseCodeSystem())
            return validateCode(url.get(), param.getCodePhrase().get());
        else if (param.isUseValueSet())
            return expandValueSet(url.get(), param.getCodePhrase().get());
        throw new IllegalStateException();
    }

    static String guaranteePrefix(String prefix, String str) {
        if (StringUtils.isEmpty(str)) return null;
        else if (str.contains(prefix)) return str;
        else return prefix + str;
    }

    @Override
    public List<DvCodedText> expand(TerminologyParam param) {
        // sanity checks
        if (param.getServiceApi().isEmpty() || !isValidTerminology(param.getServiceApi())) {
            LOG.warn("Unsupported service-api: {}", param.getServiceApi());
            return Collections.emptyList();
        }

        Optional<String> urlParam = param.extractFromParameter(p -> Optional.ofNullable(guaranteePrefix("url=", p)));

        if (urlParam.isEmpty() || param.getServiceApi().isEmpty() || !isValidTerminology(param.getServiceApi())) {
            return Collections.emptyList();
        }

        try {
            DocumentContext jsonContext = internalGet(renderTempl(EXPAND_VALUE_SET_TEMPL, baseUrl, urlParam.get()));
            return ValueSetConverter.convert(jsonContext);
        } catch (Exception e) {
            if (failOnError) throw new ExternalTerminologyValidationException(format(ERR_EXPAND_VALUESET, e));
            LOG.warn(format(ERR_EXPAND_VALUESET, e.getMessage()));
            return Collections.emptyList();
        }
    }

    abstract static class ValueSetConverter {
        private static final String CONTAINS = "$['expansion']['contains'][*]";
        private static final String SYS = "system";
        private static final String CODE = "code";
        private static final String DISP = "display";

        @SuppressWarnings("unchecked")
        static List<DvCodedText> convert(DocumentContext ctx) throws Exception {
            JSONArray read = ctx.read(CONTAINS);
            return read.stream()
                    .map(e -> (Map<String, String>) e)
                    .map(m -> new DvCodedText(m.get(DISP), new CodePhrase(new TerminologyId(m.get(SYS)), m.get(CODE))))
                    .collect(Collectors.toList());
        }
    }

    private Try<Boolean, ConstraintViolationException> validateCode(String url, CodePhrase codePhrase) {
        if (!StringUtils.equals(url, codePhrase.getTerminologyId().getValue())) {
            var constraintViolation = new ConstraintViolation(MessageFormat.format(
                    "The terminology {0} must be {1}",
                    codePhrase.getTerminologyId().getValue(), url));
            return Try.failure(new ConstraintViolationException(List.of(constraintViolation)));
        }

        DocumentContext context;
        try {
            context = internalGet(
                    baseUrl + "/CodeSystem/$validate-code?url=" + url + "&code=" + codePhrase.getCodeString());
        } catch (IOException e) {
            if (failOnError)
                throw new ExternalTerminologyValidationException(
                        "An error occurred while validating the code in CodeSystem", e);
            LOG.warn("An error occurred while validating the code in CodeSystem: {}", e.getMessage());
            return Try.success(Boolean.FALSE);
        }
        boolean result = context.read("$.parameter[0].valueBoolean", boolean.class);
        if (!result) {
            var message = context.read("$.parameter[1].valueString", String.class);
            var constraintViolation = new ConstraintViolation(message);
            return Try.failure(new ConstraintViolationException(List.of(constraintViolation)));
        }

        return Try.success(Boolean.TRUE);
    }

    private Try<Boolean, ConstraintViolationException> expandValueSet(String url, CodePhrase codePhrase) {
        DocumentContext context;
        try {
            context = internalGet(baseUrl + "/ValueSet/$expand?url=" + url);
        } catch (IOException e) {
            if (failOnError)
                throw new ExternalTerminologyValidationException("An error occurred while expanding the ValueSet", e);
            LOG.warn("An error occurred while expanding the ValueSet: {}", e.getMessage());
            return Try.success(Boolean.FALSE);
        }
        List<Map<String, String>> codings =
                context.read("$.expansion.contains[?(@.code=='" + codePhrase.getCodeString() + "')]");

        if (codings.isEmpty()) {
            var constraintViolation = new ConstraintViolation(MessageFormat.format(
                    "The value {0} does not match any option from value set {1}", codePhrase.getCodeString(), url));
            return Try.failure(new ConstraintViolationException(List.of(constraintViolation)));
        } else if (codings.size() == 1) {
            Map<String, String> coding = codings.get(0);
            if (!StringUtils.equals(
                    coding.get("system"), codePhrase.getTerminologyId().getValue())) {
                var constraintViolation = new ConstraintViolation(
                        MessageFormat.format("The terminology {0} must be  {1}", codePhrase.getCodeString(), url));
                return Try.failure(new ConstraintViolationException(List.of(constraintViolation)));
            }
        }
        return Try.success(Boolean.TRUE);
    }
}
