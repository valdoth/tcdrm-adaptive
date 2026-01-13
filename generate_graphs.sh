#!/bin/bash
set -e

cd /Users/valdo/Desktop/cloud/avancement/tcdrm-adaptive
mkdir -p images

mvn clean compile
mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.examples.TcdrmAdaptiveTraining"
mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.examples.TcdrmArticleGraphs"
mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.examples.TcdrmCombinedGraphs"
mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.examples.TcdrmMetricsGraphs"
mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.examples.TcdrmAdaptiveComparisonV2"

echo "Done."
