kafkaDir=$HOME'/RedHatTech/kafka_2.12-2.5.0.redhat-00003'
echo "Directory: "$kafkaDir
cd $kafkaDir
bin/kafka-topics.sh --delete --topic opsmgmt_platformtransactions &
## FHIR Third Party Server Integration
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic opsmgmt_platformtransactions &
## FHIR Third Party Server Integration
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic fhirsvr_allergyIntollerance &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic fhirsvr_condition &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic fhirsvr_consent &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic fhirsvr_patient &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic fhirsvr_problem &
## Financial
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic fhirsvr_claim &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic fhirsvr_coverage &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic fhirsvr_explanationofbenefits &

## FHIR Third Party Server Integration
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic ent_fhirsvr_allergyIntollerance &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic ent_fhirsvr_condition &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic ent_fhirsvr_consent &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic ent_fhirsvr_patient &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic ent_fhirsvr_problem &
## Financial
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic ent_fhirsvr_claim &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic ent_fhirsvr_coverage &
bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic ent_fhirsvr_explanationofbenefits &

