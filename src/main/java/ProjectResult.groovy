
class ProjecResult {

	String projectName;

	int consecutiveLinesonly = 0;
	int spacingonly  = 0;
	int ssmergeConf = 0;
	int jdimeConf   = 0;
	int textualConf = 0;

	long ssmergetime = 0L;
	long unmergetime = 0L;
	long jdimetime   = 0L;

	int travisBuildTime	= 0;
	int travisBuildDiscarded = 0;
	int travisPASSEDssmerge = 0;
	int travisFAILEDssmerge = 0;
	int travisERROREDssmerge = 0;
	int travisPASSEDjdime = 0;
	int travisFAILEDjdime = 0;
	int travisERROREDjdime = 0;
	
	int travisPASSEDssmergeNoCLWS = 0;
	int travisFAILEDssmergeNoCLWS = 0;
	int travisERROREDssmergeNoCLWS = 0;
	int travisPASSEDjdimeNoCLWS = 0;
	int travisFAILEDjdimeNoCLWS = 0;
	int travisERROREDjdimeNoCLWS = 0;

	int travisPASSEDssmergeNoWS = 0;
	int travisFAILEDssmergeNoWS = 0;
	int travisERROREDssmergeNoWS = 0;
	int travisPASSEDjdimeNoWS = 0;
	int travisFAILEDjdimeNoWS = 0;
	int travisERROREDjdimeNoWS = 0;


	int changedFiles = 0;
	int commonChangedFiles = 0;
	int changedMethods = 0;
	int commonChangedMethods = 0;

	int ssmergeConfNoCL = 0;
	int ssmergeConfNoWS = 0;
	int ssmergeConfNoCLWS = 0;

	int numberOfScenarios = 0;
	int conflictingScenarios = 0;
	int conflictingScenariosNoWS = 0;
	int conflictingScenariosNoCLWS = 0;
		
	int scenariosWithCL = 0;
	int scenariosWithWS = 0;
	
	int scenariosWithSsmergeConf = 0;
	int scenariosWithJdimeConf = 0;
	int scenariosWithTextualConf = 0;

	int scenariosOnlyWithSsmergeConf = 0;
	int scenariosOnlyWithJdimeConf = 0;
	int scenariosOnlyWithTextualConf = 0;
	
	int scenariosOnlyWithSsmergeConfNoCLWS = 0;
	int scenariosOnlyWithJdimeConfNoCLWS = 0;
	int scenariosOnlyWithSsmergeConfNoWS = 0;
	int scenariosOnlyWithJdimeConfNoWS = 0;
	
	int scenariosWithSsmergeConfNoCL = 0;
	int scenariosWithSsmergeConfNoWS = 0;
	int scenariosWithSsmergeConfNoCLWS = 0;

	int scenariosWithCommonChangedFiles = 0;
	int scenariosWithCommonChangedMethods = 0;
	
	public ProjecResult(){
	}
}
