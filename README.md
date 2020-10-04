# Demo-iDAAS-Connect-FHIR
Demo of iDAAS Connect FHIR capabilities

iDAAS has several key components that provide many capabilities. iDAAS Connect is intended ONLY
to enable iDAAS connectivity. iDAAS-Connect-FHIR specifically ONLY deals with enabling 
iDAAS to process the healthcare industry standard FHIR based based resources.To get the latest 
FHIR Resource support please go to the Connected Health/iDAAS 
<a href="https://www.connectedhealth-idaas.io/home/SupportedTransactions" target="_blank">
Supported Transactions page</a>. Currently, we support almost 80 FHIR Resources!!! In order to 
support features like searching a FHIR Server will be required. The iDAAS-Connect-FHIR component 
does fully support integrating to external vendor FHIR server, look in the running section 
below for more details and specifics.  

## Additional Artifacts/Apps/Data
This solution contains three supporting directories. The intent of these artifacts to enable
resources to work locally: <br/>
1. platform-addons: add-ons potentially needed. We include the AMQ-Streams release here just in case its needed.
2. platform-scripts: support running kafka, creating/listing and deleting topics needed for this solution
and also building and packaging the solution as well. All the scripts are named to describe their capabilities <br/>
3. platform-testdata: sample transactions to leverage for using the platform. 

## Scenario: Integration 
This repository follows a very common general facility based implementation. The implementation
is of a facility, we have named MCTN for an application we have named MMS. This implementation 
specifically defines one HL7 socket server endpoint per datatype mentioned above.

### Integration Data Flow Steps
This data flow is a sample flow based on real world usage and needs. When the iDAAS-Connect-FHIR starts it 
creates a servlet endpoint per defined FHIR Resource
that can be leveraged immediately. To date the iDAAS-Connect-FHIR component fullyt supports all
the FHIR resource needs for CMS Interoperability Final Rule including support for USCD1.
FHIR Servers and has been tested with multiple FHIR servers.<br/>
<b>How would I connect to iDAAS-Connect-FHIR to post data to it?</b><br/>
<i>Every FHIR supported resource is in lower case.</i> This example is based on the default 
configuration of iDAAS with no advanced configuration or HTTPS endpoints being enabled. For example, 
if the iDAAS-Connect-FHIR 
is running on localhost and you are trying to work with a FHIR Condition resource then 
the URL would be http://localhost/8080/condition.
 
1. We use Postman, a well know API testing tool, to connect to the specific FHIR resource endpoint within the 
running instance of iDAAS-Connect-FHIR. 
2. iDAAS-Connect-FHIR audits the inbound data.
3. iDAAS-Connect-FHIR then processes the data to/from the configured FHIR server
4. iDAAS-Connect-FHIR then processes the FHIR server response and audits it as well. 
    
## Builds
This section will cover both local and automated builds.

### Local Builds
Within the code base you can find the local build commands in the /platform-scripts directory
1.  Run the build-solution.sh script
It will run the maven commands to build and then package up the solution. The package will use the usual settings
in the pom.xml file. It pulls the version and concatenates the version to the output jar it builds.
Additionally, there is a copy statement to remove any specific version, so it outputs idaas-connect-hl7.jar

### Automated Builds
Automated Builds are going to be done in Azure Pipelines

## Running
Once built you can run the solution by executing `./platform-scripts/start-solution.sh`. 
The script will startup Kafka and iDAAS server.

Alternatively, if you have a running instance of Kafka, you can start a solution with:
`./platform-scripts/start-solution-with-kafka-brokers.sh --idaas.kafkaBrokers=host1:port1,host2:port2`.
The script will startup iDAAS server.

It is possible to overwrite configuration by:
1. Providing parameters via command line e.g.
`./start-solution.sh --idaas.adtPort=10009`
2. Creating an application.properties next to the idaas-connect-hl7.jar in the target directory
3. Creating a properties file in a custom location `./start-solution.sh --spring.config.location=file:./config/application.properties`

As you look at the properties the idaas.fhirVendor on startup determines the FHIR Server that would be
leveraged:
idaas.fhirVendor=ibm
idaas.fhirVendor=hapi
idaas.fhirVendor=microsoft

Supported properties include:
```properties
idaas.kafkaBrokers=localhost:9092 #a comma separated list of kafka brokers e.g. host1:port1,host2:port2
idaas.fhirVendor=ibm
idaas.ibmURI=http://localhost:8090/fhir-server/api/v4/
idaas.hapiURI=http://localhost:8080/hapi/api/v4/
idaas.msoftURI=http://localhost:9999/microsoftapi/api/v4/

## Ongoing Enhancements
We maintain all enhancements within the Git Hub portal under the 
<a href="https://github.com/RedHat-Healthcare/iDAAS-Connect-HL7/projects" target="_blank">projects tab</a>

## Defects/Bugs
All defects or bugs should be submitted through the Git Hub Portal under the 
<a href="https://github.com/RedHat-Healthcare/iDAAS-Connect-HL7/issues" target="_blank">issues tab</a>

## Chat and Collaboration
You can always leverage <a href="https://redhathealthcare.zulipchat.com" target="_blank">Red Hat Healthcare's ZuilpChat area</a>
and find all the specific areas for iDAAS-Connect-HL7. We look forward to any feedback!!

If you would like to contribute feel free to, contributions are always welcome!!!! 

Happy using and coding....


