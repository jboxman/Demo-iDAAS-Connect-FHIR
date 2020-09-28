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
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import sun.util.calendar.BaseCalendar;
import java.time.LocalDate;

@Component
public class CamelConfiguration extends RouteBuilder {
  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);

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
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=opsmgmt_platformtransactions&brokers=localhost:9092")
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
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_allergyintellorance&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/allergyintollerance?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("allergyintollerance")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("allergyintollerance FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")// Invoke External FHIR Server
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
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_consent&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/consent?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("consent")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("consent FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
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
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_condition&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/communication?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("condition")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("condition FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://measure")
        .routeId("FHIRMeasure")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Measure")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Measure message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_measure&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/measure?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("measure")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("measure FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://measurereport")
        .routeId("FHIRMeasureReport")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("MeasureReport")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Measure Report message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_measurereport&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/measurereport?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("measurereport")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("measurereport FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
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
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_patient&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/patient?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("patient")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("patient FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
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
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_problem&brokers=localhost:9092")
            //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
            //.to("jetty:http://localhost:8090/fhir-server/api/v4/patient?bridgeEndpoint=true&exchangePattern=InOut")
            //Process Response
            //.convertBodyTo(String.class)
            // set Auditing Properties
            //.setProperty("processingtype").constant("data")
            //.setProperty("appname").constant("iDAAS-Connect-FHIR")
            //.setProperty("industrystd").constant("FHIR")
            //.setProperty("messagetrigger").constant("patient")
            //.setProperty("component").simple("${routeId}")
            //.setProperty("processname").constant("Response")
            //.setProperty("camelID").simple("${camelId}")
            //.setProperty("exchangeID").simple("${exchangeId}")
            //.setProperty("internalMsgID").simple("${id}")
            //.setProperty("bodyData").simple("${body}")
            //.setProperty("auditdetails").constant("patient FHIR response message received")
            // iDAAS DataHub Processing
            //.wireTap("direct:auditing")
    ;
    from("servlet://researchstudy")
        .routeId("FHIRResearchStudy")
        .convertBodyTo(String.class)
         // set Auditing Properties
         .setProperty("processingtype").constant("data")
         .setProperty("appname").constant("iDAAS-Connect-FHIR")
         .setProperty("industrystd").constant("FHIR")
         .setProperty("messagetrigger").constant("ResearchStudy")
         .setProperty("component").simple("${routeId}")
         .setProperty("camelID").simple("${camelId}")
         .setProperty("exchangeID").simple("${exchangeId}")
         .setProperty("processname").constant("Input")
         .setProperty("internalMsgID").simple("${id}")
         .setProperty("bodyData").simple("${body}")
         .setProperty("processname").constant("Input")
         .setProperty("auditdetails").constant("Research Study message received")
         // iDAAS DataHub Processing
         .wireTap("direct:auditing")
         // Send To Topic
         .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_researchstudy&brokers=localhost:9092")
         //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
         //.to("jetty:http://localhost:8090/fhir-server/api/v4/researchstudy?bridgeEndpoint=true&exchangePattern=InOut")
         //Process Response
         //.convertBodyTo(String.class)
         // set Auditing Properties
         //.setProperty("processingtype").constant("data")
         //.setProperty("appname").constant("iDAAS-Connect-FHIR")
         //.setProperty("industrystd").constant("FHIR")
         //.setProperty("messagetrigger").constant("researchstudy")
         //.setProperty("component").simple("${routeId}")
         //.setProperty("processname").constant("Response")
         //.setProperty("camelID").simple("${camelId}")
         //.setProperty("exchangeID").simple("${exchangeId}")
         //.setProperty("internalMsgID").simple("${id}")
         //.setProperty("bodyData").simple("${body}")
         //.setProperty("auditdetails").constant("researchstudy FHIR response message received")
         // iDAAS DataHub Processing
         //.wireTap("direct:auditing")
    ;

    /*
     *
     * Financial FHIR
     *
     */
    from("servlet://http://localhost:8888/fhiraccount")
            .routeId("FHIRAccount")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-Connect-FHIR")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("account")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("account message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_account&brokers=localhost:9092")
            // Invoke External FHIR Server
            //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
            //.to("https://localhost:9443/fhir-server/api/v4/account")
            // Process Response
            //.convertBodyTo(String.class)
            // set Auditing Properties
            //.setProperty("processingtype").constant("data")
            //.setProperty("appname").constant("iDAAS-Connect-FHIR")
            //.setProperty("industrystd").constant("FHIR")
            //.setProperty("messagetrigger").constant("account")
            //.setProperty("component").simple("${routeId}")
            //.setProperty("processname").constant("Input")
            //.setProperty("camelID").simple("${camelId}")
            //.setProperty("exchangeID").simple("${exchangeId}")
            //.setProperty("internalMsgID").simple("${id}")
            //.setProperty("bodyData").simple("${body}")
            //.setProperty("auditdetails").constant("account FHIR response message received")
            // iDAAS DataHub Processing
            //.wireTap("direct:auditing")
    ;
    from("servlet://fhirrcoverage")
            .routeId("FHIRCoverage")
            .convertBodyTo(String.class)
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-Connect-FHIR")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("coverage")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("coverage message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_coverage&brokers=localhost:9092")
            // Invoke External FHIR Server
            //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
            //.to("jetty:http://localhost:8090/fhir-server/api/v4/coverage?bridgeEndpoint=true&exchangePattern=InOut")
            // Process Response
            //.convertBodyTo(String.class)
            // set Auditing Properties
            //.setProperty("processingtype").constant("data")
            //.setProperty("appname").constant("iDAAS-Connect-FHIR")
            //.setProperty("industrystd").constant("FHIR")
            //.setProperty("messagetrigger").constant("coverage")
            //.setProperty("component").simple("${routeId}")
            //.setProperty("processname").constant("Response")
            //.setProperty("camelID").simple("${camelId}")
            //.setProperty("exchangeID").simple("${exchangeId}")
            //.setProperty("internalMsgID").simple("${id}")
            //.setProperty("bodyData").simple("${body}")
            //.setProperty("auditdetails").constant("coverage FHIR message response received")
            // iDAAS DataHub Processing
            //.wireTap("direct:auditing")
    ;
     from("servlet://http://localhost:8888/fhirclaim")
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
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_claim&brokers=localhost:9092")
            // Invoke External FHIR Server
            //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
            //.to("jetty:http://localhost:8090/fhir-server/api/v4/claim?bridgeEndpoint=true&exchangePattern=InOut")
            //Process Response
            //.convertBodyTo(String.class)
            // set Auditing Properties
            //.setProperty("processingtype").constant("data")
            //.setProperty("appname").constant("iDAAS-Connect-FHIR")
            //.setProperty("industrystd").constant("FHIR")
            //.setProperty("messagetrigger").constant("claim")
            //.setProperty("component").simple("${routeId}")
            //.setProperty("processname").constant("Response")
            //.setProperty("camelID").simple("${camelId}")
            //.setProperty("exchangeID").simple("${exchangeId}")
            //.setProperty("internalMsgID").simple("${id}")
            //.setProperty("bodyData").simple("${body}")
            //.setProperty("auditdetails").constant("claim FHIR Response message received")
            // iDAAS DataHub Processing
            //.wireTap("direct:auditing")
    ;
    from("servlet://http://localhost:8888/fhirclaimresponse")
            .routeId("FHIRClaimresponse")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-Connect-FHIR")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("claimresponse")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("claimresponse message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_claimresponse&brokers=localhost:9092")
            // Invoke External FHIR Server
            //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
            //.to("jetty:http://localhost:8090/fhir-server/api/v4/claimresponse?bridgeEndpoint=true&exchangePattern=InOut")
            //Process Response
            //.convertBodyTo(String.class)
            // set Auditing Properties
            //.setProperty("processingtype").constant("data")
            //.setProperty("appname").constant("iDAAS-Connect-FHIR")
            //.setProperty("industrystd").constant("FHIR")
            //.setProperty("messagetrigger").constant("claimresponse")
            //.setProperty("component").simple("${routeId}")
            //.setProperty("processname").constant("Response")
            //.setProperty("camelID").simple("${camelId}")
            //.setProperty("exchangeID").simple("${exchangeId}")
            //.setProperty("internalMsgID").simple("${id}")
            //.setProperty("bodyData").simple("${body}")
            //.setProperty("auditdetails").constant("claim response FHIR Reponse message received")
            // iDAAS DataHub Processing
            //.wireTap("direct:auditing")
    ;
    from("servlet://http://localhost:8888/fhirexplanationofbenefits")
            .routeId("FHIRExplanationofbenefits")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-Connect-FHIR")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("explanationofbenefits")
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
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_explanationofbenefits&brokers=localhost:9092")
            // Invoke External FHIR Server
            //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
            //.to("jetty:http://localhost:8090/fhir-server/api/v4/explanationofbenefits?bridgeEndpoint=true&exchangePattern=InOut")
            //Process Response
            //.convertBodyTo(String.class)
            // set Auditing Properties
            //.setProperty("processingtype").constant("data")
            //.setProperty("appname").constant("iDAAS-Connect-FHIR")
            //.setProperty("industrystd").constant("FHIR")
            //.setProperty("messagetrigger").constant("explanationofbenefits")
            //.setProperty("component").simple("${routeId}")
            //.setProperty("processname").constant("Response")
            //.setProperty("camelID").simple("${camelId}")
            //.setProperty("exchangeID").simple("${exchangeId}")
            //.setProperty("internalMsgID").simple("${id}")
            //.setProperty("bodyData").simple("${body}")
            //.setProperty("auditdetails").constant("explanationofbenefits response FHIR response message received")
            // iDAAS DataHub Processing
            //.wireTap("direct:auditing")
    ;
    /*
     *
     * Reporting FHIR
     *
     */
  }
}