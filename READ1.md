# Performance based Service Level Agreement in Cloud Computing

**Afzal Badshah Khattak**  
Education Department, Punjab, Pakistan

**Ateeqa Jalal**  
NCBA Chenab College Mianwali, Pakistan

**Tauseef-U-Rehman**  
Faculty of Technology, Preston University, Islamabad, Pakistan

---

## Abstract

Recent advances in collaborative computing are best envisioned in cloud computing model. In cloud computing the end user are provided different services (Both in term of hardware as well as software) and are required to pay for the time and amount of services utilized. Various vendors propose different scenarios for such utilization of services. Furthermore, advances in data ware/ data mining techniques leads to generate data which are based on adhocism and all generally refined after several runs. The granularity required by such system require resources that are loosely defined in the initial phases but should be robust enough to guarantee scale ups. It is therefore imperative to have a clear cut SLA else migration of services will be disruptive with disastrous consequences for any business organization.

For proper utilization of such SLA it should have adequate mechanism for measuring/ monitoring the performance parameters. Unfortunately effective monitoring requires low level resource metric where as SLA conform to high end parameters. The situation is further complicated by the absence of frameworks that incorporate performance. More of the models are based on grid computing models which although are established but lack scalability and/or heterogeneity which are essential features of cloud computing.

In this paper we present a framework for effective monitoring of performance parameters. The model extends Web Service Level Agreement, Personalized Service Level Agreement, Automatic Service Level Agreement, Web Service Agreement and LoM2His etc which has been well received and are the defacto frameworks. We extend WSLA framework to include performance component. The component is capable of extracting parameters for performance metric. The developed framework is incorporated in simulation and the system was tested in various topological and temporal conditions. Early result shows a great deal of potential for our framework.

**Keywords:** Cloud Computing, Service Level Agreement, SLA frameworks, SLA Performance

---

## 1. Introduction

The basic idea of cloud computing is to deliver computational services across internet. The customer doesn't need to invest huge amount of money and time on software, hardware and other infrastructure [1]. It is an emerging technology which moves the services from one single desktop computer to the internet. It is just like telephone, electricity and gas billing system. Customers pay as they use. This technology is better, faster and cheaper. It contains both technical and business components. It is growing technology which delivers its services on internet. It is self managed, self descriptive and self corrective service. One cloud provider can hire many more cloud computing systems. So the provider cloud performance depends on the other hired cloud systems [2]. Cloud computing present three type of services. Software as a Service (SaaS) provides online software and application for use. Infrastructure as a Service (IaaS) provides physical infrastructure such as storage, datacenters, networking and Processors…. online. Platform as a Service (PaaS) provides development, analyzing, testing and deployment tools online [2].

Cloud computing provide services in three models. Private Cloud computing is visible just by one organization. Only those organization users can use those services. Public Cloud computing is for public. Any user can connect with public cloud and can use it and pay as how much he use. Hybrid Cloud computing is combination of private and public Cloud Computing [3][4].

For any other system will need IT expertise, up gradation and ownership for any Service but Cloud computing provide services online and no up gradation and maintenance is required. Data is provided according to location wise [5]. Cloud computing is very cheap and fast growing service. It is the future of computing world its application runs on very large data scale. Cloud computing is automatic computing [6].

Cloud computing is hot topic in research since 2006. Mainframe computer were used in 1950. Networking idea existed at this time. Time sharing technology was used back in 1960. In 1960 Mr. John Mclarthy suggested that computer resources can be delivered as public utility.CPU sharing was used at this time. In 1970 IBM released Virtual Machine operating system. After 1990 when internet bandwidth increases utility computing progress very rapidly. 1990 telecommunication give virtual private network. Salseforce.com is the first provider of cloud computing which started working in 1999. The 2nd cloud provider is the Amazone (EC2). It is the first widely used cloud computing provider. In 2009 Google started her trade as web 2.0. [7].

### Service Level Agreement (SLA)

Service Level Agreement is contract between service provider and service consumer. Detailed service level objectives are discussed in SLA negotiation. Required services, Quality of Service, performance and parameters are agreed and signed [4]. Both provider and consumer monitor services with agreed SLA. If some violation occurs then penalties are enforced on failure party. These penalties may be in credit [8].

![Figure 1: Service Level Agreement](Figure showing Customer and Provider with bidirectional arrows and Contract SLA document)

SLA life cycle contain six steps. In first step customer discover services which are closely related to their requirements. In 2nd step SLA is defined for services, penalties, and Quality of Service. In 3rd step SLA agreement is established between both parties. In 4th step services are monitored against agreed SLA. In 5th step SLA is terminated due to fulfilling time period or any violation. In 6th step penalties are enforced on party which fails to fulfill SLA [9].

![Figure 2: Service Level Agreement Life Cycle](Circular diagram showing: Discover Services → Define SLA → Establish Agreement → Monitor SLA → Terminate SLA → Enforced Penalties)

The component of Service Level Agreement are purpose, restriction, validity period, scope, parties, service level objectives, penalties, optimal services and administration. Service Level Agreement improves the Quality of Service. It ensures the SLA performance agreed between service provider and consumer. Clearly defined SLA increase the customer satisfaction and ensure the provision of customer requirements [9]. Customer monitors services according to SLA. On violation of agreed SLA service provider is informed. This agreement increase trust relationship between provider and consumer [9].

---

## 2. Service Level Agreement Related Existing Framework

Alexander Keller & Heiko Ludwig (2003) presented Web Service Level Agreement. WSLA is Service Level Agreement for Web service environment to define and monitor web services. WSLA enable service provider and consumer to define wide variety of parameters of SLA and also enable them how to relate and measure them. WSLA provide effective way to monitors the services and also guarantee the mishaps. WSLA has three components parties, service definition, and service objection. Two types of parties are discussed in WSLA. Supporting party and signatory party. Service definition component define the service objectives and its parameters. The obligation component defines the service guarantees. Web Service Level Agreement is a very good frame work for cloud computing services but unfortunately it does not discuss the performance of the services and how to convert low level metrics to high level parameters to measure them [10].

Christpher Redl, Ivan Breskovic, Ivona and Schahram (2012) worked on Automatic Service Level Agreement. It uses past knowledge, user requirement and characteristics evaluation to automatically match any SLA. SLA mapping bridges the difference of two SLA. Mapping is defined by market participant. In cloud market before creating SLA, consumers and providers put their templates of SLA. These temples contain SLA metrics, SLA parameters and Service Level Objectives. In market user match their private SLA with public SLA which is nearest to their requirements. SLA mapping are used to match two SLA templates. SLA mapping convert the same meaning word to one word and different units to one unit. It automatically searches similar SLA from the market. ASLA reduce the market cost. Automatic SLA save too much our time and cost but unfortunately it is just agreement. It does not give any guarantee to ensure Quality of Service and performance [11].

Jennifer Ortizy & Victor Teixeira (2013) worked on Personalized Service Level Agreement. It acts as broker between service provider and service consumer. PSLA hire different type of services from different service provider and then provide these services to further customers according to their needs. Without PSLA customers must have to translate their resources needs according to service provider needs. Personalized Service Level Agreement resolved this major problem. User has no to translate their requirements. Users only upload their requirement to PSLA broker and then it provides services according to customer's needs. Personalized SLA solved the major problem of service provision but Personalized SLA is combination of services and each service has different QoS and SLA [12].

Vincent C. Emeakaroha, Ivona Brandic, Michael Maurer and Schahram Daustadar (2013) worked on LoM2HiS. It is a part of FoSII infrastructure. This SLA framework gives a platform to convert low level metrics to high level parameters for measurement. This infrastructure contains the repository of mapped metrics and agreed SLA. When any new customer request comes to the system this framework mapped it with mapped metrics repository. This framework saves the customers from costly SLAs and futures failures. LoM2HiS is automatic SLA management and enforcement framework. This framework notify about future threats. This framework is the first step about Cloud computing performance measurement but it does not discuss how to measure these metrics? How to analyze and how to integrate it wit any SLA for implementation [13].

Feng chuan Zhu, Hao Li and Jan Lu (2012) worked on Cloud Bank Service Level Agreement. In Cloud Bank Service Level Agreement services are taken by service provider and this services are then stored in services pool. CBSLA is divided into two parts. One SLA is signed by service provider and cloud ban, and the second SLA is signed between service consumer and cloud bank. WSLA is also very flexible framework but it does not facilitate with support services. CBSLA provides supporting services. Cloud Bank is used for the supervision of SLA. It provides the quality of service aspects. Cloud Bank works as broker of Cloud Services. Different SLAs are negotiated with different Cloud service provider and consumer. Pooling services is also a difficult task so it is very complex to use and implement [14].

---

## 3. Design of Performance based Service Level Agreement

In Performance based Service Level Agreement we extend WSLA framework to include performance component. The component is capable of extracting parameters for performance metric.

![Figure 3: Performance based Service Level Agreement](Architecture diagram showing PSLA components: Parties (Provider, Consumer, Monitoring), ServiceDefinition (Service objectives, Service Parameters with Unit/Function/Metric), Obligation (ServiceLevelObjec, Action Guaranty), Performance, connected to Agreed SLA, Mapped Metrics, Service/Reqest Monitor, Adjust, Resource, Performance Analyzer, and Enactor Component)

Figure 3 show the architecture of Performance based Service Level Agreement. Three types of parties take part in Service Level Agreement. Provider party provides services, Customer party takes services from service provider. Monitoring party monitors the services according to agreed SLA and ensures the Quality of Service and performance. Service definition defines the services which are provided. Parameters are specified by metrics. Metrics are used to measure the service parameters. Obligation has the conditional structure. It checks the services and if service is not satisfied then penalties are enforced on the failure party. Performance component is integrated with Web Service Level Agreement, to measure low level metric and to ensure the Quality of Service and Service performance.

When SLA negotiation process is completed then its results and rules are stored in agreed SLA repository. This repository also stores the market related SLAs. All services are checked with agreed SLA to determine the violations. These values are also used to reduce the future SLA violation. The service / request monitor is designed to monitor the services and customers request with agreed SLA. If it agrees then its mapping is created by monitor using mapped metrics repository which contain predefined mapping rules. Mapped metric repository stores values which are specific to that problem. Mapping is done in Domain Specific Language (DSL). Monitor measure the deployed services with the help of mapped values to ensure performance. If the customer request is according to the agreed SLA then monitor notify to the enactor component to adjust the resources. The Adjust Resources component picks the required services from the resource component and provides it to the customer through monitor. The monitor checks these services with agreed SLA. Performance analyzer component check the performance of the services it notify the enactor component to adjust the performance of the service. If the resource component fails to provide required rate of service the performance component notify the action component and the SLA cycle is stopped and removed.

---

## 4. Experimental Design

Web Service Level Agreement is extended to incorporate our model. The following items were added to the existing SLA mode i) Runtime Monitor ii) SLA Repository iii) Mapped metric repository iv) Enactor component v) Adjust Resources and vi) Performance analyzer component. The resultant model was executed on CloudSim which is checking and analyzing the general structure of Performance based Service Level Agreement. We are conducting this test to measure the scalability and performance of the framework.

![Figure 4: CloudSim Simulation Architecture of PSLA](Layered architecture showing User Data (Cloud Scenario, User Requireme, Application Configuration, Performance Analyzer), Scheduling Polices (User or Data Center Broker, Visualizer), CloudSim layers (User Interface Structure with Cloudlet/Virtual Machine/Monitor, VMs Services with Cloudlet Execution/VM Management, Cloud Services with VM Provision/Allocation/Memory/Storage/Bandwidth/Info Gather, Cloud Resources with Event Handling/Sensor/Cloud Coordinator/Data Center, Network with Network Topology/Message Delay Calculation), and CloudSim Core Simulation Engine)

Figure 4 shows the CloudSim simulation architecture for Performance based Service Level Agreement. Performance Analyzer Component analyzes the request and services with agreed/ negotiated SLAs and generate warning if the services do not fulfill the negotiated SLAs. Visualizer uses different scheduling algorithms to allocate VMs to the different workload to fulfill customer's requests. Monitor monitors user request and provider services with agreed SL. Direct visualizer to allocate VMs to the tasks according to their load. InfoGather gathers data from all the interacted modules. [15]

![Figure 5: Simulation for experimental test of PSLA](Diagram showing Resources (VM 1, VM 2, VM 300) connected to Adjust Resources, which connects to Service/Request Monitor, which connects to Customers (VM 1, VM 2, VM n))

In this simulator resources pool is created which contain 300 Virtual Machine providing resources. On other hand n virtual machines are created which send request to the service/request monitor. The monitor gets the metrics of resources by XML files and then transmits it to communication channel. We created a large number of resources VM and large number of customers VM to create the real environment to check the performance of monitor in detail. This performance component is part of SLA so we stored some agreed parameters and their threshold values in SLA repository (Table 1). We are using i3, 2MB L2 Cache, 3GB RAM, and 300 HD and Linux operating system on which CloudSim is installed.

### Table 1: Agreed Parameters

| SLA Parameter | SLA Objective | Threshold Value |
|---------------|---------------|-----------------|
| Response | 98% | 98.9 % |
| Availability | 500 ms | 498.9 |
| Memory | 100 GB | 102 GB |
| Storage | 3 GB | 3.9 GB |
| Bandwidth | 50 Mbit/s | 102 Mbit/s |

Our simulation environment is able to detect the SLA violation. Monitor and Enactor component is looping and conditional structure which repeatedly checks the service request and service supply with agreed SLA repository as presented in Table 1. The looping structure repeats the services to deliver good quality services and the conditional structure notify enactor and action component for future decision. This looping and conditional structure ignores violations in two situations i) when the services are first time initialized or ii) when services are executed after upgrade request. The service/request monitor performance depend on three parameters i) resources metrics measure time ii) mapping the measure metrics values with customer requirement and integration, and iii) sending these values to the communication channel.

---

## 5. Evaluation Result

In this stage we are presenting the experimental result of Performance based Service Level Agreement for different scenario. We mainly focus on VM Management, VM Provision and scheduling policies by using low level metric parameters to monitor the performance of the services. In this simulation, adjust resources component decide that how many Virtual Machines should provide to satisfy customer request. Three polices are usually used in cloud computing VMs provision. These are Maximize Throughput Provision Policy, Minimize Response time Provision Policy and Maximize Utilization Provision Policy [16]. We check performance of our framework by these three polices.

Four types of work load 3000, 6000, 10000 and 20000 were tested. Simulation results are checked for Mean Execution Time and Deadline Satisfaction rate. Figure no 6 gives the graph between numbers of tasks and means execution time. MTPP mean execution time is 390,430,700 and 800 respectively for the work loads, MRPP mean time execution time is 400,450,700 and 780 respectively for the work loads, MUPP mean time execution time is 430,530,600 and 700 respectively for the work loads. Results shows that Maximize Utilization Provision Policy is very good, as the workload increases its Mean Execution Time decreases.

![Figure 6: Execution time of VM with different polices](Bar graph showing Mean Execution Time vs Number of Tasks for MTPP, MRPP, and MUPP)

Figure no 7 gives the graph between numbers of tasks and numbers of deadline satisfaction. It shows that MTPP deadlock satisfaction rate is 72,65,55 and 40 respectively for the work loads, MRPP deadlock satisfaction rate is 70,63,52 and 30 respectively for the work loads and MUPP deadlock satisfaction rate is 73,70,53 and 40 respectively for the work loads. Graph shows that Maximize Utilization Provision Policy is satisfying large number of deadlock with respect to other provision polices.

![Figure 7: Rate of threshold values with different polices](Bar graph showing Number of Deadline Satisfaction vs Number of tasks for MTPP, MRPP, and MUPP)

Above results shows that Maximize Utilization Provision Policy is very good and effectively utilizing VMs for different dynamic loads, so we conclude that MUPP is best for VM utilization and deadline satisfaction.

Price mechanisms shows that how much the customer is charge when it accesses the resources. Usually four types of price mechanisms are used in Cloud Computing. These are Fixed Price, Capability based Price, Market based Price and Auction based Price [16]. We checked every price mechanisms with Maximize Utilization Provision Policies. Figure 8 shows a graph drawn between number of tasks and mean execution time. FP mean execution time with the number of task 3000,6000,10000 and 20000 is 58, 70, 72 and 68. CP mean execution time is 45,65,70 and 60 respectively for the number of tasks. MP mean time execution time is 32,50, 52 and 65 respectively for the number of tasks. AP mean execution time is 30,35,45 and 55 respectively for the number of tasks.

![Figure 8: Mean Execution Time for different Price Mechanisms](Bar graph showing Mean Execution Time vs Number of Tasks for FP, CP, MP, and AP)

Results shows that in Capability based Price Mechanism when number of tasks increases then its mean execution time decreases. CP is showing very good results with respect to others price mechanisms.

Figure 9 shows graph drawn between number of tasks and resource utilization rate. Resource utilization for the number of tasks 3000, 6000,10000 and 18000 with FP is 58,40,30 and 23. CP resource utilization is 70,62,50 and 28 respectively for the number of tasks. MP resource utilization rate is 77,70,52 and 40 respectively for the number of tasks. AP resource utilization rate is 65,58,65 and 50 respectively for the number of tasks.

![Figure 9: Resource Utilization for different Price Mechanisms](Bar graph showing Resource Utilization Rate vs Number of Tasks for FP, CP, MP, and AP)

Results shows that Capability based Price Mechanism is utilizing small number of resources as the number of tasks increases. CP is giving very reliable results.

Now a days in cloud computing Fixed Price is very largely used but our results as shown in figure 8 and 9 that Capability based Price Mechanism is showing very best results for services utilization and flexible price mechanism.

Adjust resources component use scheduling algorithms to allocate VMs to the heavy tasks. Four types of algorithms are usually used in Cloud Computing. These are Round Robin Algorithm, Task Duplication Algorithm, Cluster Minimal Algorithm and Capability based Algorithm. Figure 10 shows a graph drawn between number Virtual Machines and Mean Execution Time. CRA mean execution time with number of VMs 50, 100 150, 200, 250, 300 and 500 is 100, 250, 300, 350, 1050, 2000 and 3000. RRA mean execution with the same VMs is 200, 240, 390, 340, 1040, 1800 and 2500 respectively for the number of VMs. CMA mean execution time is 200, 240, 390, 340, 1040, 1800 and 2400 respectively for the number of VMs. TDA k=2 mean execution time is 200, 240, 390, 340, 1040, 1600 and 2200 respectively for the number of VMs. TDA with 3 replica is 200, 240,390, 340, 1040, 1550 and 2000 respectively for the number of VMs.

![Figure 10: Mean Execution time with MP mechanism](Bar graph showing Mean Execution Time vs Number of Virtual Machines for CRA, RRA, CMA, TDA K=2, and TDA K=3)

As the results shows in figure 9, TDA with 3 replicas is showing very best performance. With the increasing number of Virtual Machines its mean execution time decrease with respect to other algorithms.

---

## 6. Conclusion and future work

Performance is one of the important objective of Service Level Agreement. In this paper we focused on Performance based Service Level Agreement. We extended Service Level Agreement and include performance component in it. This framework converts low level metrics to high level parameters to measure them and analyze performance of the cloud system against agreed SLA. We measured mean execution time, deadline satisfaction rate, price mechanisms and Virtual Machines allocation to ensure the service performance.

Further research is required in the parameters measurement methods and to analyze its performance in real Cloud computing environment.

---

## References

[1]. Lee Gillam, Bin Li and John O'Loughlin "Adding Cloud Performance to the service Level Agreement" International Conference on Cloud Computing and Services Science. Year 2012, Page No 621-630

[2]. Key Ruan, Joshua James, Joe Carthy and Tahar Keechadi j" Key Terms For Service Level Agreement to support Cloud Forensics" IFIP Int. Conf. Digital Forensics 2012.

[3]. Sona Dubey and Sanjay Agrwal "Quality of Service Task Scheduling in Cloud Computing" International Journal of Computer Application Technology and Research, Volume 2, 2013

[4]. Linlin Wu, Saurabh Kumar Garg and Rajkumar Buya "SLA –based Resource Alloction for Software as a Service Provider (SaaS) in Cloud computing Environment" International Conference on Cloud Computing and Services Science. Year 2010

[5]. Cloud Standard customer council "Cloud Service Level Agreement for Cloud Services" ETSI TR 103 125 V1.1.1(2012)

[6]. Claude Baudoin, Jordan Flyn and John McDonland "Public Cloud Service Level Agreement: what to expect and what to negotiate" Cloud Standard Customer Council, March 30, 2013.

[7]. Http://blog.softlayer.com/2013/virtual-magic-the-cloud April 2014

[8]. Kaouthar Fakhfakh and Tarak Chaari "Semantic Enabled Framework for SLA monitoring" International Journal on Advanced in Software, Vol 2 no 1, 2009.

[9]. Linlin Wu and Rajkumar Buyya "Service Level Agreement (SLA) in utility computing System" International Conference on Cloud Computing and Services Science. Year 2010

[10]. Alexander Keller and Heiko Ludwig "The Web Service Level Agreement: Specifying and monitoring Service Level Agreement for Web Services" Journal of Network and Systems Management, Vol. 11, No. 1, March 2003

[11]. Christpher Redl, Ivan Breskovic, Ivona and Schahram "Automatic SLA Matching and Provider selection in Grid and Cloud Computing Environments" GRID 2012

[12]. Jennifer Ortizy and Victor Teixeira "A Vision of Personalized Service Level Agreement" DanaC'13, June 23, 2013, New York, NY, USA

[13]. Vincent C. Emeakaroha, Ivona Brandic, Michael Maurer and Schahram DAustadar "Low Level Metric to Highs Level SLAs- LoM2HiS Framework: Bridging the gap between monitored metrics and SLA parameters in cloud environments" HPCS 2010 page no 48-54

[14]. Zhu, Tengchaun, Li, Hao and Lu "A Service Level Agreement Framework of Cloud computing based on Cloud Bank Model" Computer Science and Automation Engineering (CSAE), 2012 IEEE International Conference, May 25, 2012, page no 255-259.

[15]. Http://saas-cloud-simulation.wikispaces.asu.edu June 13, 2014

[16]. Peng Xiao and Hui Lin "An Extensible performance Evaluation Framework for Cloud Computing Systems" international Journal of Future Generation communication and network Vol.6 No. 4 August 2013.

[17]. Carolina, Jacques, Walfredo and Mirna "Independent Auditing Service Level Agreement in the Grid" Future Generation Computer System 2006.

[18]. D.Lamannas, J.Skene and W.Emmerich "A Language for defining Service Level Agreement" Distributed Computing Systems, 2003. FTDCS 2003. Proceedings. The Ninth IEEE Workshop on Future Trends, May 28, 2003, page no 100-106

[19]. Liang Zhao, Sherif Sakr and Anna Liu "A Framework for Consumer-Centric SLA Management of Cloud-Hosted database" IEEE Transections on services computing, February 14, 2013

[20]. Stefano, Ferretti, Cittorio ghini, Fabio, Panzieri, Michele and Elsisa Terrini "Quality of Service aware Cloud" Cloud Computing (CLOUD), 2010 IEEE 3rd International Conference, July 10, 2010, page no 321-328

[21]. Asousa, Leoomoreira, Gsantos and Javaamg "Quality of Service for Database in the Cloud"

[22]. Christos A, Y Foulis and Anatasios Gounaris "Honoring SLAs on Cloud Computing Services: A control perspective"

[23]. Mario Mac, J. Oriol Fil and Jordi Guitart "Rule-based SLA management for revenue mazimizatin in cloud computing markets" IEEE Computer Society Publications, 2010.

[24]. Mohammad Firdhous, Suhaidi Hassan and Osman Ghjazali "A comprehensive survey on Quality of Service implementation in Cloud Computing" International Journal of Scientific & Engineering Research, Feb 10, 2014

[25]. Daniel A. Menasce and Emiliano CAsalicchio "Quality of Service in Grid Computing" IEEE august 2004.

[26]. Javier Conejero, Blanca Caminero and Carmen "A SLA-based Framework with support for meta scheduling in advanced for Grids"

[27]. Sarwan Singh and Manish Arora "Monitoring and Controlling multi level SLA in Cloud Environment using agent" International Journal of Advance Research in Computer and Software Engineering. July 2013.

[28]. Giuseppe Cicotti, Luigi Coppolino and Rosario Cristaldi "QoS monitoring in a Cloud Services Environment: The SRT-15 approach" Euro-Par 2011: Parallel Processing Workshops, 2012, page no 15-24.

[29]. Salvatore Venticinque, Rocco Aversa and Beniamino di martino "A Cloud agency for SLA Negotiation and management" Euro-Par 2010 Parallel Processing Workshops Lecture Notes in Computer Science Volume 6586, 2011, pp 587-594

[30]. Mandeep Devgan and Anwalvir Singh j" A study of different QoS management techniques in cloud Computing" International Journal of Soft Computing and Engineering .July 2013.

[31]. Zheng Li, Liam O'Beren, He Zhang and Rainbow Cai "A factor framework for experimental design for performance evaluation of commercial Cloud Services"

[32]. Nezih jYigitabasi and Simon Osternm "C-Meter: A Framework for performance analysis of Computing Clouds" 9th IEEE ACM International Symposium on Cluster Computing and Grid, 2009

[33]. Saurabh Kumar Garg, Steve VErsteeg and Rajkumar Buyya "A Framework for ranking of Cloud Computing Services" Future Generation Computer System 2013.

[34]. Keith R Jackson, Krishna and Shane Canon "Performance analysis of high performance computing application on the Amazon web services cloud" IEEE 2010.

[35]. Rich C.Lee "Smart System Resource monitoring in Cloud" iBusiness Scientific Research, March 2012

[36]. Luis Bautista, Alain abran and Alain April "Design of a Performance measurement Framework for Cloud Computing" A Journal of Software Research and Application 2011.

[37]. Aalexandru Iosup and Radu Prodan "Performance analysis of Cloud Computing services for many tasks scientific computing" IEEE 2010.

[38]. Mintu M.ladani and Vinit Kumar Gupta "A Framework for performance analysis of Cloud Computing" International Journal of Innovative Technology and Exploring Engineering ISSN: 2278-3075 Volume 2, May 6, 2013.

[39]. Jisha S. Mangaly and Jisha S "A Comparative study on open source Cloud Computing Framework" International Journal of Communication System, 2013.

[40]. Pankesh patel, Ajith Ranabahn and Amit Sheth "Service Level Agreement" 2009

---

*Source: Research Journal of Science & IT Management*  
*Volume: 04, Number: 04, February-2015*  
*ISSN: 2251-1563*  
*Pages: 20-30*