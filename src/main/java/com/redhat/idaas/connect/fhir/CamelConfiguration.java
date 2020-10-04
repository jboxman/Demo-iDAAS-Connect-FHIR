/*
 * Copyright 2019 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */
package com.redhat.idaas.connect.fhir;

import ca.uhn.fhir.store.IAuditDataStore;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import sun.util.calendar.BaseCalendar;
import java.time.LocalDate;

@Component
public class CamelConfiguration extends RouteBuilder {
  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);

  @Autowired
  private ConfigProperties config;

  @Bean
  private KafkaEndpoint kafkaEndpoint(){
    KafkaEndpoint kafkaEndpoint = new KafkaEndpoint();
    return kafkaEndpoint;
  }
  @Bean
  private KafkaComponent kafkaComponent(KafkaEndpoint kafkaEndpoint){
    KafkaComponent kafka = new KafkaComponent();
    return kafka;
  }

  @Bean
  ServletRegistrationBean camelServlet() {
    // use a @Bean to register the Camel servlet which we need to do
    // because we want to use the camel-servlet component for the Camel REST service
    ServletRegistrationBean mapping = new ServletRegistrationBean();
    mapping.setName("CamelServlet");
    mapping.setLoadOnStartup(1);
    mapping.setServlet(new CamelHttpTransportServlet());
    mapping.addUrlMappings("/iDAAS/*");
    return mapping;
  }

  private String getKafkaTopicUri(String topic) {
    return "kafka:" + topic +
            "?brokers=" +
            config.getKafkaBrokers();
  }

  private String getFHIRServerUri(String fhirServerVendor, String fhirResource) {
    String fhirServerURI = null;
    if (fhirServerVendor.equals("ibm"))
    {
      //.to("jetty:http://localhost:8090/fhir-server/api/v4/AdverseEvents?bridgeEndpoint=true&exchangePattern=InOut")
      fhirServerURI = config.getIbmFhirServer()+fhirResource+"?bridgeEndpoint=true&exchangePattern=InOut";
    }
    if (fhirServerVendor.equals("hapi"))
    {
      fhirServerURI = config.getHapiFhirServer()+fhirResource+"?bridgeEndpoint=true&exchangePattern=InOut";
    }
    if (fhirServerVendor.equals("microsoft"))
    {
      fhirServerURI = config.getMsftAzureFhirServer()+fhirResource+"?bridgeEndpoint=true&exchangePattern=InOut";
    }
    return fhirServerURI;
  }
  /*
   * Kafka implementation based upon https://camel.apache.org/components/latest/kafka-component.html
   *
   */
  @Override
  public void configure() throws Exception {

    /*
     * Audit
     *
     * Direct component within platform to ensure we can centralize logic
     * There are some values we will need to set within every route
     * We are doing this to ensure we dont need to build a series of beans
     * and we keep the processing as lightweight as possible
     *
     */
    from("direct:auditing")
        .setHeader("messageprocesseddate").simple("${date:now:yyyy-MM-dd}")
        .setHeader("messageprocessedtime").simple("${date:now:HH:mm:ss:SSS}")
        .setHeader("processingtype").exchangeProperty("processingtype")
        .setHeader("industrystd").exchangeProperty("industrystd")
        .setHeader("component").exchangeProperty("componentname")
        .setHeader("messagetrigger").exchangeProperty("messagetrigger")
        .setHeader("processname").exchangeProperty("processname")
        .setHeader("auditdetails").exchangeProperty("auditdetails")
        .setHeader("camelID").exchangeProperty("camelID")
        .setHeader("exchangeID").exchangeProperty("exchangeID")
        .setHeader("internalMsgID").exchangeProperty("internalMsgID")
        .setHeader("bodyData").exchangeProperty("bodyData")
        //.convertBodyTo(String.class).to("kafka://localhost:9092?topic=opsmgmt_platformtransactions&brokers=localhost:9092")
        .convertBodyTo(String.class).to(getKafkaTopicUri("opsmgmt_platformtransactions"))
    ;
    /*
    *  Logging
    */
    from("direct:logging")
        .log(LoggingLevel.INFO, log, "HL7 Admissions Message: [${body}]")
        //To invoke Logging
        //.to("direct:logging")
    ;

    /*
     *  Clinical FHIR
     *  ----
     * these will be accessible within the integration when started the default is
     * <hostname>:8080/idaas/<resource>
     *
     */
    from("servlet://alergyintollerance")
        .routeId("FHIRAllergyIntollerance")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("messagetrigger").constant("AllergyIntollerance")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Allergy Intollerance message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        //.convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_allergyintellorance&brokers=localhost:9092")
        .convertBodyTo(String.class).to(getKafkaTopicUri("fhirsvr_allergyintellorance"))
        .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/AllergyIntollerance?bridgeEndpoint=true&exchangePattern=InOut")
        .to("jetty:"+getFHIRServerUri(config.getFhirServerVendor(),"AllergyIntollerance"))
        //Process Response
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("AllergyIntollerance")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Response")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Allergy Intollerance response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")// Invoke External FHIR Server
    ;
    from("servlet://condition")
        .routeId("FHIRCondition")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Condition")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Condition message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        //.convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_condition&brokers=localhost:9092")
        .convertBodyTo(String.class).to(getKafkaTopicUri("fhirsvr_condition"))
        .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Condition?bridgeEndpoint=true&exchangePattern=InOut")
        .to("jetty:"+getFHIRServerUri(config.getFhirServerVendor(),"Condition"))
        //Process Response
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Condition")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Response")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("condition response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
    ;
    from("servlet://consent")
        .routeId("FHIRConsent")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Consent")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Consent message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        .convertBodyTo(String.class).to(getKafkaTopicUri("fhirsvr_consent"))
        .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Consent?bridgeEndpoint=true&exchangePattern=InOut")
        .to("jetty:"+getFHIRServerUri(config.getFhirServerVendor(),"Consent"))
        //Process Response
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Consent")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Response")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Consent response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
    ;
    from("servlet://patient")
        .routeId("FHIRPatient")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Patient")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Patient message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to(getKafkaTopicUri("fhirsvr_patient"))
        .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Patient?bridgeEndpoint=true&exchangePattern=InOut")
        .to("jetty:"+getFHIRServerUri(config.getFhirServerVendor(),"Patient"))
        //Process Response
        .convertBodyTo(String.class)
        //set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Patient")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Response")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Patient response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
    ;
    from("servlet://problem")
            .routeId("FHIRProblem")
            .convertBodyTo(String.class)
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-Connect-FHIR")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("Problem")
            .setProperty("component").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("Problem message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            //.convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_problem&brokers=localhost:9092")
            .convertBodyTo(String.class).to(getKafkaTopicUri("fhirsvr_problem"))
            .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
            //.to("jetty:http://localhost:8090/fhir-server/api/v4/Problem?bridgeEndpoint=true&exchangePattern=InOut")
            .to("jetty:"+getFHIRServerUri(config.getFhirServerVendor(),"Problem"))
            //Process Response
            //.convertBodyTo(String.class)
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-Connect-FHIR")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("Problem")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Response")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("Problem response message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
    ;

    /*
     *
     * Financial FHIR
     *
     */
    from("servlet://account")
            .routeId("FHIRAccount")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-Connect-FHIR")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("Account")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("Account message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            //.convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_account&brokers=localhost:9092")
            .convertBodyTo(String.class).to(getKafkaTopicUri("fhirsvr_account"))
            .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
            //.to("jetty:http://localhost:8090/fhir-server/api/v4/Account?bridgeEndpoint=true&exchangePattern=InOut")
            .to("jetty:"+getFHIRServerUri(config.getFhirServerVendor(),"Account"))
            // Invoke External FHIR Server
            // Process Response
            .convertBodyTo(String.class)
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-Connect-FHIR")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("Account")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("Account response message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
    ;
    from("servlet://coverage")
        .routeId("FHIRCoverage")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Coverage")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Coverage message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        //.convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_coverage&brokers=localhost:9092")
        .convertBodyTo(String.class).to(getKafkaTopicUri("fhirsvr_coverage"))
        .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Coverage?bridgeEndpoint=true&exchangePattern=InOut")
        .to("jetty:"+getFHIRServerUri(config.getFhirServerVendor(),"Coverage"))
        // Process Response
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Coverage")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Response")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Coverage message response received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
    ;
     from("servlet://http://claim")
         .routeId("FHIRClaim")
         // set Auditing Properties
         .setProperty("processingtype").constant("data")
         .setProperty("appname").constant("iDAAS-Connect-FHIR")
         .setProperty("industrystd").constant("FHIR")
         .setProperty("messagetrigger").constant("claim")
         .setProperty("component").simple("${routeId}")
         .setProperty("processname").constant("Input")
         .setProperty("camelID").simple("${camelId}")
         .setProperty("exchangeID").simple("${exchangeId}")
         .setProperty("internalMsgID").simple("${id}")
         .setProperty("bodyData").simple("${body}")
         .setProperty("auditdetails").constant("Claim message received")
         // iDAAS DataHub Processing
         .wireTap("direct:auditing")
         // Send To Topic
         //.convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_claim&brokers=localhost:9092")
         .convertBodyTo(String.class).to(getKafkaTopicUri("fhirsvr_claims"))
         .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
         //.to("jetty:http://localhost:8090/fhir-server/api/v4/Claim?bridgeEndpoint=true&exchangePattern=InOut")
         .to("jetty:"+getFHIRServerUri(config.getFhirServerVendor(),"Claim"))
         /*
         Process Response
         */
         .convertBodyTo(String.class)
         //set Auditing Properties
         .setProperty("processingtype").constant("data")
         .setProperty("appname").constant("iDAAS-Connect-FHIR")
         .setProperty("industrystd").constant("FHIR")
         .setProperty("messagetrigger").constant("claim")
         .setProperty("component").simple("${routeId}")
         .setProperty("processname").constant("Response")
         .setProperty("camelID").simple("${camelId}")
         .setProperty("exchangeID").simple("${exchangeId}")
         .setProperty("internalMsgID").simple("${id}")
         .setProperty("bodyData").simple("${body}")
         .setProperty("auditdetails").constant("claim FHIR Response message received")
         //iDAAS DataHub Processing
         .wireTap("direct:auditing")
     ;
    from("servlet://explanationofbenefits")
        .routeId("FHIRExplanationofbenefits")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ExplanationOfBenefits")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("explanationofbenefits message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        //.convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_explanationofbenefits&brokers=localhost:9092")
        .convertBodyTo(String.class).to(getKafkaTopicUri("fhirsvr_explanatonofbenefits"))
        // Invoke External FHIR Server
        .setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ExplanationOfBenefits?bridgeEndpoint=true&exchangePattern=InOut")
        .to("jetty:"+getFHIRServerUri(config.getFhirServerVendor(),"ExplanationOfBenefits"))
        //Process Response
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ExplanationOfBenefits")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Response")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("ExplanationOfBenefits response response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
    ;
    /*
     *
     * Reporting FHIR
     *
     */
  }
}